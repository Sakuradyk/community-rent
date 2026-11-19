package com.duyikang.rental.module.order.service;

import com.duyikang.rental.module.order.dto.OrderCreateDTO;
import com.duyikang.rental.module.order.dto.OrderQuoteDTO;
import com.duyikang.rental.module.order.vo.OrderCreatedVO;
import com.duyikang.rental.module.order.vo.OrderDetailVO;
import com.duyikang.rental.module.order.vo.OrderQuoteVO;

/**
 * 订单服务（切片 3 只做"试算 / 下单 / 详情"三件事；状态流转在切片 4）。
 *
 * <p>契约见 {@code docs/接口文档.md} §3。所有方法都要求已登录（由 Security 层拦截）。</p>
 */
public interface OrderService {

    /**
     * 费用试算：算钱 + 判冲突，**不写库**。
     *
     * @throws com.duyikang.rental.common.exception.BizException 2001 参数/日期不合法、
     *                                                         3003 物品不可租、3004 物品不存在、
     *                                                         3006 自租、4090 时段冲突（带冲突日期与候选时段）
     */
    OrderQuoteVO quote(OrderQuoteDTO dto);

    /**
     * 创建订单：预检 → Redis 锁 → 事务内写订单 + 逐日占用（K2 三层）。
     *
     * @throws com.duyikang.rental.common.exception.BizException 同上，另加抢锁超时也是 4090
     */
    OrderCreatedVO create(OrderCreateDTO dto);

    /**
     * 订单详情，仅出租方 / 承租方 / 管理员可见。
     *
     * @throws com.duyikang.rental.common.exception.BizException 3005 订单不存在、3008 无权查看
     */
    OrderDetailVO detail(Long orderId);
}
