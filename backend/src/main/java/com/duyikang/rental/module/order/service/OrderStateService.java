package com.duyikang.rental.module.order.service;

import com.duyikang.rental.module.order.dto.OrderCancelDTO;

/**
 * 订单状态机（切片 4）。
 *
 * <pre>
 * PENDING --confirm--> CONFIRMED --pickup--> IN_USE --(到期扫描)--> OVERDUE
 *    \                    /
 *     \---- cancel ------ /----> CANCELLED        IN_USE/OVERDUE --return--> COMPLETED（切片 5，需算钱）
 * </pre>
 *
 * <p><b>为什么单独一个 Service：</b>这里的每个动作都要"改状态 + 改物品状态 + 释放占用"三件事同成败，
 * 必须整体在一个事务里；而 {@code OrderServiceImpl} 刻意不标 {@code @Transactional}（下单时锁要在事务外），
 * 把状态流转放进单独类，可以让"事务边界"一眼看清，也避免两类语义混在一个类里。</p>
 *
 * <p><b>为什么用条件更新（CAS）而不是"先查再改"：</b>状态流转都是
 * {@code UPDATE ... WHERE id=? AND status=<期望的旧状态>}，受影响行数为 0 就说明别人已经改过了，
 * 直接报 3001（{@code ORDER_STATE_INVALID}）。这样两个管理员同时点"确认"不会双写，也不需要额外加锁。</p>
 *
 * <p>契约见 {@code docs/接口文档.md} §4。</p>
 */
public interface OrderStateService {

    /**
     * 出租方确认出租：{@code PENDING → CONFIRMED}。
     *
     * @throws com.duyikang.rental.common.exception.BizException 3005 订单不存在、3008 非出租方、
     *                                                         3001 当前状态不允许确认
     */
    void confirm(Long orderId);

    /**
     * 出租方标记已取用：{@code CONFIRMED → IN_USE}，同时物品转 {@code RENTED}。
     *
     * @throws com.duyikang.rental.common.exception.BizException 同上
     */
    void pickup(Long orderId);

    /**
     * 取消订单：{@code PENDING / CONFIRMED → CANCELLED}，**释放占用**，物品恢复在架。
     *
     * <p>承租方与出租方都能在取用前取消；已进入 {@code IN_USE} 后不允许取消（只能走归还）。
     * 确认后取消的信誉扣分（{@code penalty_cancel_after_confirm}）在切片 5 随 K4 一起落地。</p>
     *
     * @throws com.duyikang.rental.common.exception.BizException 3005、3008（与订单无关的人）、
     *                                                         3001 已取用/已终结
     */
    void cancel(Long orderId, OrderCancelDTO dto);

    /**
     * 逾期扫描：把 {@code end_date < 今天} 且仍 {@code IN_USE} 的订单转 {@code OVERDUE}。
     *
     * <p>定时任务与管理端"立即执行逾期扫描"按钮共用这一个方法 —— 演示时点按钮就等于手动跑一次。</p>
     *
     * @return 本次被转为 OVERDUE 的订单数
     */
    int scanOverdue();
}
