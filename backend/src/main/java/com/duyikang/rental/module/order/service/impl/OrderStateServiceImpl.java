package com.duyikang.rental.module.order.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.duyikang.rental.common.constant.ErrorCode;
import com.duyikang.rental.common.constant.ItemStatus;
import com.duyikang.rental.common.constant.MessageType;
import com.duyikang.rental.common.constant.OrderStatus;
import com.duyikang.rental.common.exception.BizException;
import com.duyikang.rental.module.credit.service.CreditService;
import com.duyikang.rental.module.item.entity.Item;
import com.duyikang.rental.module.item.mapper.ItemMapper;
import com.duyikang.rental.module.order.dto.OrderCancelDTO;
import com.duyikang.rental.module.order.entity.OrderOccupancy;
import com.duyikang.rental.module.order.entity.RentalOrder;
import com.duyikang.rental.module.message.service.MessageService;
import com.duyikang.rental.module.order.mapper.OrderOccupancyMapper;
import com.duyikang.rental.module.order.mapper.RentalOrderMapper;
import com.duyikang.rental.module.order.service.OrderStateService;
import com.duyikang.rental.module.wallet.service.AccountService;
import com.duyikang.rental.security.CurrentUser;
import com.duyikang.rental.security.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单状态机实现。
 *
 * <p>三条硬规矩（改了就会出线上事故）：</p>
 * <ol>
 *   <li><b>状态迁移一律用条件更新</b>：{@code UPDATE ... WHERE id=? AND status=<旧状态>}，
 *       影响行数 0 即视为状态冲突（3001 ORDER_STATE_INVALID）。先查再改在并发下会双写。</li>
 *   <li><b>取消必须释放占用</b>：删 {@code order_occupancy} 里该订单的所有行，否则那几天永久被占，
 *       别人再也租不到（只做"占"不做"放"是最常见的翻车点）。</li>
 *   <li><b>物品状态跟着订单走</b>：取用 → {@code RENTED}；取消 → 若当前是 {@code RENTED} 则回 {@code ON_SHELF}。
 *       物品状态只由订单状态机驱动，不允许前端直接改。</li>
 * </ol>
 *
 * <p>契约见 {@code docs/接口文档.md} §4；状态机见 {@code 社区物品租用平台-需求总结.md} §6。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderStateServiceImpl implements OrderStateService {

    /** 取用前允许取消的状态（取用之后只能走归还，因为物品已经在对方手里） */
    private static final List<String> CANCELLABLE_STATUSES = List.of(OrderStatus.PENDING, OrderStatus.CONFIRMED);

    private final RentalOrderMapper rentalOrderMapper;
    private final OrderOccupancyMapper orderOccupancyMapper;
    private final ItemMapper itemMapper;
    private final CreditService creditService;
    private final MessageService messageService;
    private final AccountService accountService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long orderId) {
        LoginUser user = CurrentUser.require();
        RentalOrder order = loadOrder(orderId);
        requireOwner(order, user);

        int rows = rentalOrderMapper.update(null, Wrappers.<RentalOrder>lambdaUpdate()
                .set(RentalOrder::getStatus, OrderStatus.CONFIRMED)
                .set(RentalOrder::getConfirmedAt, LocalDateTime.now())
                .eq(RentalOrder::getId, orderId)
                .eq(RentalOrder::getStatus, OrderStatus.PENDING));
        requireChanged(rows, orderId, "确认", OrderStatus.PENDING);

        // 通知承租方：订单已确认（旁路通知，失败不影响主流程）
        messageService.notify(order.getRenterId(), MessageType.ORDER, "租赁申请已通过",
                "「" + order.getOrderNo() + "」已被出租方确认，请按约定时间取用。",
                MessageType.BIZ_ORDER, orderId);

        log.info("订单确认 orderId={} ownerId={}", orderId, user.userId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pickup(Long orderId) {
        LoginUser user = CurrentUser.require();
        RentalOrder order = loadOrder(orderId);
        requireOwner(order, user);

        int rows = rentalOrderMapper.update(null, Wrappers.<RentalOrder>lambdaUpdate()
                .set(RentalOrder::getStatus, OrderStatus.IN_USE)
                .set(RentalOrder::getInUseAt, LocalDateTime.now())
                .eq(RentalOrder::getId, orderId)
                .eq(RentalOrder::getStatus, OrderStatus.CONFIRMED));
        requireChanged(rows, orderId, "标记取用", OrderStatus.CONFIRMED);

        // 物品转租出中：只影响展示状态，不影响占用（占用在下单时就已经写好了）
        itemMapper.update(null, Wrappers.<Item>lambdaUpdate()
                .set(Item::getStatus, ItemStatus.RENTED)
                .eq(Item::getId, order.getItemId())
                .in(Item::getStatus, ItemStatus.ON_SHELF, ItemStatus.RENTED));

        messageService.notify(order.getRenterId(), MessageType.ORDER, "物品已标记取用",
                "「" + order.getOrderNo() + "」的出租方已标记取用，归还时请点「我已归还」申报成色。",
                MessageType.BIZ_ORDER, orderId);

        log.info("订单取用 orderId={} itemId={} ownerId={}", orderId, order.getItemId(), user.userId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long orderId, OrderCancelDTO dto) {
        LoginUser user = CurrentUser.require();
        RentalOrder order = loadOrder(orderId);

        // 与订单有关的两方都能取消；管理员可以代为取消（申诉/客诉场景）
        boolean participant = user.userId().equals(order.getOwnerId()) || user.userId().equals(order.getRenterId());
        if (!participant && !CurrentUser.isAdmin()) {
            throw new BizException(ErrorCode.PERMISSION_DENIED_FOR_RESOURCE, "只有订单双方或管理员可以取消订单");
        }
        if (!CANCELLABLE_STATUSES.contains(order.getStatus())) {
            throw new BizException(ErrorCode.ORDER_STATE_INVALID,
                    OrderStatus.IN_USE.equals(order.getStatus()) || OrderStatus.OVERDUE.equals(order.getStatus())
                            ? "物品已被取用，只能走归还流程，不能取消"
                            : "订单已结束，无法取消");
        }

        String cancelBy = user.userId().equals(order.getRenterId()) ? "RENTER" : "OWNER";
        if (CurrentUser.isAdmin() && !participant) {
            cancelBy = "ADMIN";
        }
        String reason = dto == null || dto.reason() == null || dto.reason().isBlank()
                ? "用户主动取消" : dto.reason().trim();

        int rows = rentalOrderMapper.update(null, Wrappers.<RentalOrder>lambdaUpdate()
                .set(RentalOrder::getStatus, OrderStatus.CANCELLED)
                .set(RentalOrder::getCancelledAt, LocalDateTime.now())
                .set(RentalOrder::getCancelBy, cancelBy)
                .set(RentalOrder::getCancelReason, reason)
                .eq(RentalOrder::getId, orderId)
                .in(RentalOrder::getStatus, CANCELLABLE_STATUSES));
        requireChanged(rows, orderId, "取消", OrderStatus.PENDING);

        // K4：已确认后被取消，发起取消的一方扣 3 分（出租方确认过又反悔属于违约；管理员代取消不计分）
        if (OrderStatus.CONFIRMED.equals(order.getStatus()) && !"ADMIN".equals(cancelBy)) {
            creditService.apply(user.userId(), orderId, "CANCEL_AFTER_CONFIRM", -3,
                    "出租方已确认后取消订单，信誉 -3");
        }

        // 释放占用：这是 K2 的"放"，漏了就会把那几天永久占死
        int released = releaseOccupancy(orderId);

        // 解冻资金：把该订单的「租金 + 押金」全额退回可用余额（无冻结记录的老订单会被跳过）
        accountService.releaseForOrder(order, "订单取消，解冻全部金额：" + reason);

        // 物品恢复在架（只有被标记过 RENTED 的才需要恢复）
        itemMapper.update(null, Wrappers.<Item>lambdaUpdate()
                .set(Item::getStatus, ItemStatus.ON_SHELF)
                .eq(Item::getId, order.getItemId())
                .eq(Item::getStatus, ItemStatus.RENTED));

        // 通知另一方：取消是对等的动作，谁取消就通知谁对面
        Long counterparty = user.userId().equals(order.getRenterId()) ? order.getOwnerId() : order.getRenterId();
        String actor = "RENTER".equals(cancelBy) ? "承租方" : ("OWNER".equals(cancelBy) ? "出租方" : "管理员");
        messageService.notify(counterparty, MessageType.ORDER, "订单已取消",
                actor + "取消了「" + order.getOrderNo() + "」，原因：" + reason + "。该时段已重新开放。",
                MessageType.BIZ_ORDER, orderId);

        log.info("订单取消 orderId={} by={} userId={} 释放占用={} 行 reason={}",
                orderId, cancelBy, user.userId(), released, reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int scanOverdue() {
        LocalDate today = LocalDate.now();
        int rows = rentalOrderMapper.update(null, Wrappers.<RentalOrder>lambdaUpdate()
                .set(RentalOrder::getStatus, OrderStatus.OVERDUE)
                .lt(RentalOrder::getEndDate, today)
                .eq(RentalOrder::getStatus, OrderStatus.IN_USE));
        if (rows > 0) {
            log.info("逾期扫描完成：{} 单转为 OVERDUE（判定条件 end_date < {} 且 status = IN_USE）", rows, today);
        }
        return rows;
    }

    // ------------------------------------------------------------------ 内部

    private RentalOrder loadOrder(Long orderId) {
        RentalOrder order = rentalOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        return order;
    }

    private void requireOwner(RentalOrder order, LoginUser user) {
        if (!user.userId().equals(order.getOwnerId())) {
            throw new BizException(ErrorCode.PERMISSION_DENIED_FOR_RESOURCE, "只有出租方可以执行这个操作");
        }
    }

    /**
     * 条件更新没改到行，只有两种可能：别人已经改过状态，或订单已被删。
     * 统一报 3001 并带上当前状态，便于前端刷新。
     */
    private void requireChanged(int rows, Long orderId, String action, String expectedStatus) {
        if (rows > 0) {
            return;
        }
        RentalOrder latest = rentalOrderMapper.selectById(orderId);
        String current = latest == null ? "（订单已不存在）" : OrderStatus.labelOf(latest.getStatus());
        throw new BizException(ErrorCode.ORDER_STATE_INVALID,
                "无法" + action + "：需要订单处于「" + OrderStatus.labelOf(expectedStatus) + "」，当前是「" + current + "」");
    }

    private int releaseOccupancy(Long orderId) {
        return orderOccupancyMapper.delete(Wrappers.<OrderOccupancy>lambdaQuery()
                .eq(OrderOccupancy::getOrderId, orderId));
    }
}
