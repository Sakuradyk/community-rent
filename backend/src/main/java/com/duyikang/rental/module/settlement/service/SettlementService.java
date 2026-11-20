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
     * 出租方验收并确认归还：订单完成 + 释放占用 + 生成结算单（PENDING_CONFIRM）+ 资金流水 + 损耗日志 + 信誉变动。
     *
     * <p>承租方可以先{@link #requestReturn 发起归还申请}，也可以由出租方直接确认（线下当场归还的常见情形）。
     * 出租方确认时以**自己验收的状态**为准。</p>
     *
     * @param orderId 订单 id
     * @param dto     归还请求（归还状态、备注）
     * @return 生成的结算单详情
     */
    SettlementDetailVO returnItem(Long orderId, OrderReturnDTO dto);

    /**
     * 承租方发起归还申请：只记时间戳（订单状态不变，仍是 IN_USE / OVERDUE）。
     *
     * <p>用于解决"承租方已经还了、出租方拖着不点确认"的单边权力死角：
     * 申请后出租方须在 {@code order.return.confirm.hours}（默认 24）小时内验收确认，
     * 超时由 {@link #scanReturnTimeout()} 按承租方申报的状态自动结算。</p>
     *
     * @param orderId 订单 id
     * @param dto     归还状态（承租方申报）+ 备注
     */
    void requestReturn(Long orderId, OrderReturnDTO dto);

    /**
     * 归还申请超时扫描：承租方已申报、出租方超过窗口未确认的订单，按承租方申报状态自动结算。
     *
     * @return 本次自动结算的订单数
     */
    int scanReturnTimeout();

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
