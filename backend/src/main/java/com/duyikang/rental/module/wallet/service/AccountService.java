package com.duyikang.rental.module.wallet.service;

import com.duyikang.rental.common.result.PageResult;
import com.duyikang.rental.module.order.entity.RentalOrder;
import com.duyikang.rental.module.wallet.vo.AdminAccountVO;
import com.duyikang.rental.module.wallet.vo.AdminEntryVO;
import com.duyikang.rental.module.wallet.vo.ReconcileVO;
import com.duyikang.rental.module.wallet.vo.WalletEntryVO;
import com.duyikang.rental.module.wallet.vo.WalletVO;

import java.math.BigDecimal;

/**
 * 钱包与复式账务服务。
 *
 * <p>所有资金动作都收敛到这里，业务侧只调用这五个方法（冻结/解冻/结算/裁决冲正/充值），
 * 每个方法内部都会：①用条件更新改余额（余额不足直接失败，不做「先查再改」）；
 * ②写一条成对的分录（出账方减、入账方加，金额相等），从而满足「全站余额之和恒为 0」。</p>
 *
 * @author duyikang
 */
public interface AccountService {

    /**
     * 取钱包快照；账户不存在时懒创建（新用户第一次看钱包就能看到 0）。
     *
     * @param userId 用户 id
     * @return 三行余额
     */
    WalletVO wallet(Long userId);

    /**
     * 模拟充值：语义是「用户从自己的银行卡/其它账户转入」，不接任何支付渠道。
     *
     * @param userId 用户 id
     * @param amount 金额（> 0 且 ≤ {@code wallet.recharge.max}）
     * @return 充值后的钱包快照
     */
    WalletVO recharge(Long userId, BigDecimal amount);

    /**
     * 我的资金流水（分页，按时间倒序）。
     *
     * @param userId   用户 id
     * @param pageNum  页码，从 1 起
     * @param pageSize 每页条数
     * @return 分页结果
     */
    PageResult<WalletEntryVO> myEntries(Long userId, Integer pageNum, Integer pageSize);

    /**
     * 下单冻结：把「租金 + 押金」从可用余额挪进冻结余额，余额不足报 4005。
     *
     * @param order 刚创建的订单（含金额快照）
     */
    void freezeForOrder(RentalOrder order);

    /**
     * 取消/未取用时解冻：把该订单冻结的钱全额退回可用余额。
     *
     * <p>幂等：若该订单没有冻结分录（例如本功能上线前创建的订单），直接跳过，
     * 否则会给用户凭空"印"出一笔钱。</p>
     *
     * @param order  订单
     * @param remark 说明
     */
    void releaseForOrder(RentalOrder order, String remark);

    /**
     * 结算划转：租金、逾期费、损耗费全部归物主（出租方），押金剩余退回承租方。
     *
     * @param order        订单
     * @param rentAmount   租金
     * @param overdueFee   逾期费
     * @param lossFee      损耗费
     * @param refundAmount 押金应退金额
     */
    void settleOrder(RentalOrder order, BigDecimal rentAmount, BigDecimal overdueFee,
                     BigDecimal lossFee, BigDecimal refundAmount);

    /**
     * 裁决冲正：按应退差额在承租方与物主之间反向补一笔。
     *
     * @param order  订单
     * @param delta  应退金额变化（正数=退给承租方，负数=从承租方收回给物主）
     * @param remark 说明
     */
    void verdictAdjust(RentalOrder order, BigDecimal delta, String remark);

    /**
     * 管理端账户列表。
     *
     * @param keyword  昵称/用户 id 关键字
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    PageResult<AdminAccountVO> adminAccounts(String keyword, Integer pageNum, Integer pageSize);

    /**
     * 管理端全局分录。
     *
     * @param bizType  业务类型过滤
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    PageResult<AdminEntryVO> adminEntries(String bizType, Integer pageNum, Integer pageSize);

    /**
     * 管理端手工调整某个用户余额（可正可负）。
     *
     * @param userId 用户 id
     * @param amount 调整金额（正=加款，负=扣款）
     * @param remark 理由
     * @return 调整后的钱包快照
     */
    WalletVO adminAdjust(Long userId, BigDecimal amount, String remark);

    /**
     * 对账自检：全站余额之和是否为 0、分录条数、账户数。
     *
     * @return 对账结果
     */
    ReconcileVO reconcile();
}
