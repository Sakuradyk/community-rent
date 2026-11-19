package com.duyikang.rental.module.order;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.duyikang.rental.common.constant.ErrorCode;
import com.duyikang.rental.common.exception.BizException;
import com.duyikang.rental.module.item.entity.Item;
import com.duyikang.rental.module.item.mapper.ItemMapper;
import com.duyikang.rental.module.order.dto.OrderCancelDTO;
import com.duyikang.rental.module.order.dto.OrderCreateDTO;
import com.duyikang.rental.module.order.entity.OrderOccupancy;
import com.duyikang.rental.module.order.entity.RentalOrder;
import com.duyikang.rental.module.order.mapper.OrderOccupancyMapper;
import com.duyikang.rental.module.order.mapper.RentalOrderMapper;
import com.duyikang.rental.module.order.service.OrderService;
import com.duyikang.rental.module.order.service.OrderStateService;
import com.duyikang.rental.module.order.vo.OrderCreatedVO;
import com.duyikang.rental.security.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 切片 4 订单状态流转集成测试（确认 / 取用 / 取消 / 逾期扫描）。
 *
 * <p>核心断言不是"状态变了"，而是三件事：<b>状态迁移合法</b>、<b>取消会释放占用</b>、
 * <b>物品状态跟着订单走</b>。第二条是 K2 的"放"，漏了那几天就被永久占死。</p>
 *
 * <p>一律 {@code @Transactional} 回滚；物品用 {@link #seedOnShelfItem(long)} 现造，
 * 不依赖种子行（用户会在浏览器里改动种子数据）。</p>
 */
@SpringBootTest
@Transactional
class OrderStateIntegrationTest {

    private static final Long OWNER = 2L;
    private static final Long RENTER = 3L;
    private static final Long STRANGER = 4L;

    @Autowired
    private OrderService orderService;
    @Autowired
    private OrderStateService orderStateService;
    @Autowired
    private RentalOrderMapper rentalOrderMapper;
    @Autowired
    private OrderOccupancyMapper orderOccupancyMapper;
    @Autowired
    private ItemMapper itemMapper;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("确认：出租方把 PENDING 改成 CONFIRMED，并写入 confirmed_at")
    void confirm_byOwner_movesToConfirmed() {
        Long itemId = seedOnShelfItem(OWNER);
        Long orderId = createOrder(itemId, RENTER);

        loginAs(OWNER, "张伟", "USER");
        orderStateService.confirm(orderId);

        RentalOrder order = rentalOrderMapper.selectById(orderId);
        assertThat(order.getStatus()).isEqualTo("CONFIRMED");
        assertThat(order.getConfirmedAt()).isNotNull();
    }

    @Test
    @DisplayName("确认：承租方不能替出租方确认 → 3008")
    void confirm_byRenter_forbidden() {
        Long itemId = seedOnShelfItem(OWNER);
        Long orderId = createOrder(itemId, RENTER);

        loginAs(RENTER, "李娜", "USER");
        assertThatThrownBy(() -> orderStateService.confirm(orderId))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.PERMISSION_DENIED_FOR_RESOURCE.getCode());
                    assertThat(e.getMessage()).contains("只有出租方");
                });
    }

    @Test
    @DisplayName("取用：确认后才能取用，订单转 IN_USE 且物品变 RENTED")
    void pickup_afterConfirm_movesToInUse_andItemRented() {
        Long itemId = seedOnShelfItem(OWNER);
        Long orderId = createOrder(itemId, RENTER);

        loginAs(OWNER, "张伟", "USER");
        orderStateService.confirm(orderId);
        orderStateService.pickup(orderId);

        RentalOrder order = rentalOrderMapper.selectById(orderId);
        assertThat(order.getStatus()).isEqualTo("IN_USE");
        assertThat(order.getInUseAt()).isNotNull();
        assertThat(itemMapper.selectById(itemId).getStatus()).isEqualTo("RENTED");
    }

    @Test
    @DisplayName("取用：还没确认就想取用 → 3003（状态机不许跳步）")
    void pickup_beforeConfirm_stateInvalid() {
        Long itemId = seedOnShelfItem(OWNER);
        Long orderId = createOrder(itemId, RENTER);

        loginAs(OWNER, "张伟", "USER");
        assertThatThrownBy(() -> orderStateService.pickup(orderId))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.ORDER_STATE_INVALID.getCode());
                    assertThat(e.getMessage()).contains("已确认");
                });
        assertThat(rentalOrderMapper.selectById(orderId).getStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("取消：PENDING 取消后占用被删干净、订单转 CANCELLED、cancel_by=RENTER")
    void cancel_pending_releasesOccupancy() {
        Long itemId = seedOnShelfItem(OWNER);
        Long orderId = createOrder(itemId, RENTER);

        // 下单即占时段：3 天 → 3 行占用
        assertThat(occupancyRows(orderId)).isEqualTo(3);

        loginAs(RENTER, "李娜", "USER");
        orderStateService.cancel(orderId, new OrderCancelDTO("临时不需要了"));

        RentalOrder order = rentalOrderMapper.selectById(orderId);
        assertThat(order.getStatus()).isEqualTo("CANCELLED");
        assertThat(order.getCancelBy()).isEqualTo("RENTER");
        assertThat(order.getCancelReason()).isEqualTo("临时不需要了");
        assertThat(order.getCancelledAt()).isNotNull();

        // K2 的"放"：占用行必须一条不剩，否则那几天永久被占
        assertThat(occupancyRows(orderId)).isZero();

        // 取消后同样时段可以再被别人下单（说明占用真的释放了，不是只改了状态）
        loginAs(STRANGER, "王强", "USER");
        LocalDate start = LocalDate.now().plusDays(20);
        OrderCreatedVO again = orderService.create(new OrderCreateDTO(itemId, start, start.plusDays(3), null));
        assertThat(again.status()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("取消：取用之后不能取消，只能走归还 → 3003")
    void cancel_afterPickup_stateInvalid() {
        Long itemId = seedOnShelfItem(OWNER);
        Long orderId = createOrder(itemId, RENTER);

        loginAs(OWNER, "张伟", "USER");
        orderStateService.confirm(orderId);
        orderStateService.pickup(orderId);

        assertThatThrownBy(() -> orderStateService.cancel(orderId, null))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.ORDER_STATE_INVALID.getCode());
                    assertThat(e.getMessage()).contains("只能走归还");
                });
        assertThat(occupancyRows(orderId)).isEqualTo(3);
    }

    @Test
    @DisplayName("取消：与订单无关的人不能取消 → 3008")
    void cancel_byStranger_forbidden() {
        Long itemId = seedOnShelfItem(OWNER);
        Long orderId = createOrder(itemId, RENTER);

        loginAs(STRANGER, "王强", "USER");
        assertThatThrownBy(() -> orderStateService.cancel(orderId, null))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PERMISSION_DENIED_FOR_RESOURCE.getCode()));
        assertThat(rentalOrderMapper.selectById(orderId).getStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("逾期扫描：只把 end_date 已过且仍 IN_USE 的订单转 OVERDUE，CONFIRMED 不动")
    void scanOverdue_marksOnlyPastInUse() {
        Long itemId = seedOnShelfItem(OWNER);
        Long inUsePastId = seedOrder(itemId, "IN_USE", LocalDate.now().minusDays(5), LocalDate.now().minusDays(2));
        Long confirmedPastId = seedOrder(itemId, "CONFIRMED", LocalDate.now().minusDays(5), LocalDate.now().minusDays(2));
        Long inUseFutureId = seedOrder(itemId, "IN_USE", LocalDate.now().plusDays(1), LocalDate.now().plusDays(3));

        int updated = orderStateService.scanOverdue();

        assertThat(updated).isGreaterThanOrEqualTo(1);
        assertThat(rentalOrderMapper.selectById(inUsePastId).getStatus()).isEqualTo("OVERDUE");
        assertThat(rentalOrderMapper.selectById(confirmedPastId).getStatus()).isEqualTo("CONFIRMED");
        assertThat(rentalOrderMapper.selectById(inUseFutureId).getStatus()).isEqualTo("IN_USE");

        // 幂等：再扫一次不会重复计数（OVERDUE 已不在 IN_USE 条件里）
        assertThat(orderStateService.scanOverdue()).isZero();
    }

    // ------------------------------------------------------------------ 工具

    /** 现造一件在架物品（owner 名下），避免依赖会被用户改动的种子行 */
    private Long seedOnShelfItem(long ownerId) {
        Item item = new Item();
        item.setOwnerId(ownerId);
        item.setCategoryId(8L);
        item.setCommunityId(1L);
        item.setTitle("状态机测试物品");
        item.setDescription("仅测试用");
        item.setCoverUrl("/uploads/202611/" + "0".repeat(32) + ".png");
        item.setDailyPrice(new BigDecimal("20.00"));
        item.setDeposit(new BigDecimal("100.00"));
        item.setOverdueRate(new BigDecimal("0.5000"));
        item.setLossValue(BigDecimal.ZERO);
        item.setStatus("ON_SHELF");
        itemMapper.insert(item);
        return item.getId();
    }

    /** 走真实下单链路（会写订单 + 逐日占用），返回订单 id */
    private Long createOrder(Long itemId, Long renterId) {
        loginAs(renterId, "李娜", "USER");
        LocalDate start = LocalDate.now().plusDays(20);
        OrderCreatedVO created = orderService.create(new OrderCreateDTO(itemId, start, start.plusDays(3), "状态机测试"));
        return created.id();
    }

    /** 直接插一笔指定状态的订单（用于逾期扫描这类无法用正常链路造出的状态） */
    private Long seedOrder(Long itemId, String status, LocalDate start, LocalDate end) {
        RentalOrder order = new RentalOrder();
        order.setOrderNo("T" + System.nanoTime());
        order.setItemId(itemId);
        order.setOwnerId(OWNER);
        order.setRenterId(RENTER);
        order.setStartDate(start);
        order.setEndDate(end);
        order.setRentDays((int) java.time.temporal.ChronoUnit.DAYS.between(start, end));
        order.setStatus(status);
        order.setDailyPrice(new BigDecimal("20.00"));
        order.setDeposit(new BigDecimal("100.00"));
        order.setOverdueRate(new BigDecimal("0.5000"));
        order.setRentAmount(new BigDecimal("60.00"));
        rentalOrderMapper.insert(order);
        return order.getId();
    }

    private long occupancyRows(Long orderId) {
        return orderOccupancyMapper.selectCount(Wrappers.<OrderOccupancy>lambdaQuery()
                .eq(OrderOccupancy::getOrderId, orderId));
    }

    private void loginAs(Long userId, String nickname, String role) {
        LoginUser user = new LoginUser(userId, nickname, role);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
}
