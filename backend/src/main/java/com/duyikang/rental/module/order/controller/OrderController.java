package com.duyikang.rental.module.order.controller;

import com.duyikang.rental.common.result.PageResult;
import com.duyikang.rental.common.result.Result;
import com.duyikang.rental.module.order.dto.OrderCancelDTO;
import com.duyikang.rental.module.order.dto.OrderCreateDTO;
import com.duyikang.rental.module.order.dto.OrderQuoteDTO;
import com.duyikang.rental.module.order.service.OrderService;
import com.duyikang.rental.module.order.service.OrderStateService;
import com.duyikang.rental.module.order.vo.OrderCreatedVO;
import com.duyikang.rental.module.order.vo.OrderDetailVO;
import com.duyikang.rental.module.order.vo.OrderListItemVO;
import com.duyikang.rental.module.order.vo.OrderQuoteVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单接口（切片 3：试算 / 下单 / 详情 / 列表；切片 4：确认 / 取用 / 取消）。
 *
 * <p>所有接口都要求登录（Security 默认 {@code anyRequest().authenticated()}，
 * 本路径没有进白名单）。Controller 只做"收参数 + 包 Result"，业务与校验顺序全在 Service。</p>
 *
 * <p>状态流转的"谁能做"由 Service 判（出租方 / 双方 / 管理员），Controller 不重复判断 ——
 * 权限判断只写一处，改了不会漏。</p>
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final OrderStateService orderStateService;

    /**
     * 费用试算：不写库。冲突时返回 4090 + 冲突日期与候选时段（见接口文档 §3.1）。
     */
    @PostMapping("/quote")
    public Result<OrderQuoteVO> quote(@Valid @RequestBody OrderQuoteDTO dto) {
        return Result.ok(orderService.quote(dto));
    }

    /**
     * 创建订单：服务端重算金额，成功返回待确认订单（已占时段）。
     */
    @PostMapping
    public Result<OrderCreatedVO> create(@Valid @RequestBody OrderCreateDTO dto) {
        return Result.ok(orderService.create(dto));
    }

    /**
     * 我的订单列表：{@code role} 取 {@code renter}（我租的）/ {@code owner}（我出租的）/ {@code all}，
     * 可按状态过滤，按创建时间倒序分页（见接口文档 §3.6）。
     */
    @GetMapping
    public Result<PageResult<OrderListItemVO>> list(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "1") Integer pageNum,
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return Result.ok(orderService.list(role, status, pageNum, pageSize));
    }

    /**
     * 订单详情：仅出租方 / 承租方 / 管理员可见。
     */
    @GetMapping("/{id}")
    public Result<OrderDetailVO> detail(@PathVariable Long id) {
        return Result.ok(orderService.detail(id));
    }

    // ------------------------------------------------------------------ 切片 4：状态流转

    /**
     * 出租方确认出租：{@code PENDING → CONFIRMED}（接口文档 §4.1）。
     */
    @PostMapping("/{id}/confirm")
    public Result<Void> confirm(@PathVariable Long id) {
        orderStateService.confirm(id);
        return Result.ok();
    }

    /**
     * 出租方标记已取用：{@code CONFIRMED → IN_USE}，物品转「租出中」（接口文档 §4.2）。
     */
    @PostMapping("/{id}/pickup")
    public Result<Void> pickup(@PathVariable Long id) {
        orderStateService.pickup(id);
        return Result.ok();
    }

    /**
     * 取消订单：{@code PENDING / CONFIRMED → CANCELLED}，释放占用（接口文档 §4.3）。
     *
     * <p>理由可以为空（默认「用户主动取消」），所以 body 用 {@code required = false}。</p>
     */
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id, @Valid @RequestBody(required = false) OrderCancelDTO dto) {
        orderStateService.cancel(id, dto);
        return Result.ok();
    }
}
