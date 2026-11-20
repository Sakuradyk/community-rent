package com.duyikang.rental.module.settlement.service;

import com.duyikang.rental.module.settlement.dto.OrderReturnDTO;
import com.duyikang.rental.module.settlement.dto.SettlementDisputeDTO;
import com.duyikang.rental.module.settlement.vo.SettlementDetailVO;

/**
 * 结算服务（K3）：归还 → 生成结算单（冷静期）→ 承租方确认 / 提异议 → 超时自动结算。
 *
 * <p>对账口径见 `docs/接口文档.md` §5.0；金额一律用 {@code BigDecimal}，
 * 逾期费与损耗费的计算全部委托 `FeeCalculator`（纯函数，便于单测与论文核对）。</p>
 */
public interface SettlementService {

    /**
     * 出租方标记归还：订单完成 + 释放占用 + 生成结算单（PENDING_CONFIRM）+ 资金流水 + 损耗日志 + 信誉变动
     *
     * @param orderId 订单 id
     * @param dto     归还请求（归还状态、备注）
     * @return 生成的结算单详情
     */
    SettlementDetailVO returnItem(Long orderId, OrderReturnDTO dto);

    /**
     * 按订单查结算单（仅订单双边与管理员）
     *
     * @param orderId 订单 id
     * @return 结算单详情
     */
    SettlementDetailVO detailByOrder(Long orderId);

    /**
     * 承租方确认无异议：PENDING_CONFIRM → SETTLED
     *
     * @param settlementId 结算单 id
     */
    void confirm(Long settlementId);

    /**
     * 承租方提出异议：PENDING_CONFIRM → DISPUTED，并生成一条待处理申诉
     *
     * @param settlementId 结算单 id
     * @param dto          异议内容
     */
    void dispute(Long settlementId, SettlementDisputeDTO dto);

    /**
     * 把超过冷静期仍未表态的结算单批量转为 SETTLED（管理端按钮与定时任务共用）
     *
     * @return 本次处理的条数
     */
    int scanConfirmTimeout();
}
