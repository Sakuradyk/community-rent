package com.duyikang.rental.module.settlement.calc;

import com.duyikang.rental.common.constant.ReturnState;
import com.duyikang.rental.module.loss.entity.LossPolicy;
import com.duyikang.rental.module.order.entity.RentalOrder;
import com.duyikang.rental.module.settlement.entity.FeePolicy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * K3 费用与损耗计算器（纯函数，无状态、无数据库访问）。
 *
 * <p>为什么单独抽出来：这是论文里要答辩的公式，集中在一个文件里才能被逐条核对与单测；
 * 散在 Service 里就会变成"改一处忘一处"。所有口径见 `docs/接口文档.md` §5.0。</p>
 */
public final class FeeCalculator {

    /**
     * 金额统一两位小数
     */
    public static final int MONEY_SCALE = 2;

    /**
     * 损耗值统一四位小数（与 DECIMAL(6,4) 对齐）
     */
    public static final int RATIO_SCALE = 4;

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private FeeCalculator() {
    }

    /**
     * 逾期天数 = max(0, 实际归还日 − endDate)，结束日当天归还算 0 天
     *
     * @param order        订单
     * @param actualReturn 实际归还日
     * @return 逾期天数
     */
    public static int overdueDays(RentalOrder order, LocalDate actualReturn) {
        long days = ChronoUnit.DAYS.between(order.getEndDate(), actualReturn);
        return days > 0 ? (int) days : 0;
    }

    /**
     * 逾期费 = 日租金 × [前 tier1Days 天 × 订单快照费率 + 超出天数 × 订单快照费率 × tier2/tier1]
     *
     * <p>用订单快照的 `overdueRate` 而不是策略表的绝对值：价格三件套下单即冻结，之后改策略不影响历史订单。
     * `tier2Rate / tier1Rate` 是"第二档相对第一档的倍数"，默认 1.0/0.5 = 2 倍。</p>
     *
     * @param order      订单（提供 dailyPrice 与 overdueRate 快照）
     * @param overdueDays 逾期天数
     * @param policy     费用策略
     * @return 逾期费（两位小数）
     */
    public static BigDecimal overdueFee(RentalOrder order, int overdueDays, FeePolicy policy) {
        if (overdueDays <= 0) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
        int tier1Days = policy.getOverdueTier1Days() == null ? 3 : policy.getOverdueTier1Days();
        int firstDays = Math.min(overdueDays, tier1Days);
        int extraDays = Math.max(0, overdueDays - tier1Days);

        BigDecimal rate = order.getOverdueRate() == null ? BigDecimal.ZERO : order.getOverdueRate();
        BigDecimal tier2Factor = BigDecimal.ONE;
        if (policy.getOverdueTier1Rate() != null && policy.getOverdueTier1Rate().compareTo(BigDecimal.ZERO) > 0) {
            tier2Factor = policy.getOverdueTier2Rate().divide(policy.getOverdueTier1Rate(), RATIO_SCALE, RoundingMode.HALF_UP);
        }

        BigDecimal perDay = rate.multiply(BigDecimal.valueOf(firstDays))
                .add(rate.multiply(tier2Factor).multiply(BigDecimal.valueOf(extraDays)));
        return order.getDailyPrice().multiply(perDay).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 本次损耗值 w = 租期系数 × 类目系数 × 归还状态系数
     *
     * @param rentDays    租期天数
     * @param categoryCoef 类目损耗系数（category.loss_coef）
     * @param returnState 归还状态
     * @param policy      损耗策略
     * @return w（四位小数）
     */
    public static BigDecimal lossWeight(int rentDays, BigDecimal categoryCoef, String returnState, LossPolicy policy) {
        BigDecimal termCoef = termCoef(rentDays, policy);
        BigDecimal stateCoef = returnCoef(returnState, policy);
        BigDecimal cat = categoryCoef == null ? BigDecimal.ZERO : categoryCoef;
        return termCoef.multiply(cat).multiply(stateCoef).setScale(RATIO_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 租期系数：≤ tier1MaxDays 用 tier1Coef，≤ tier2MaxDays 用 tier2Coef，否则 tier3Coef
     *
     * @param rentDays 租期天数
     * @param policy   损耗策略
     * @return 租期系数
     */
    public static BigDecimal termCoef(int rentDays, LossPolicy policy) {
        if (policy.getTermTier1MaxDays() != null && rentDays <= policy.getTermTier1MaxDays()) {
            return policy.getTermTier1Coef();
        }
        if (policy.getTermTier2MaxDays() != null && rentDays <= policy.getTermTier2MaxDays()) {
            return policy.getTermTier2Coef();
        }
        return policy.getTermTier3Coef();
    }

    /**
     * 归还状态系数：完好 1.0 / 轻微磨损 1.5 / 损坏 3.0（取值来自 loss_policy）
     *
     * @param returnState 归还状态
     * @param policy      损耗策略
     * @return 归还状态系数
     */
    public static BigDecimal returnCoef(String returnState, LossPolicy policy) {
        return switch (returnState) {
            case ReturnState.WORN -> policy.getReturnCoefWorn();
            case ReturnState.DAMAGED -> policy.getReturnCoefDamaged();
            default -> policy.getReturnCoefIntact();
        };
    }

    /**
     * 累计损耗 L_after = min(1, L_before + w)
     *
     * @param lossBefore 归还前累计损耗
     * @param weight     本次损耗值
     * @return 归还后累计损耗（四位小数，上限 1）
     */
    public static BigDecimal lossAfter(BigDecimal lossBefore, BigDecimal weight) {
        BigDecimal before = lossBefore == null ? BigDecimal.ZERO : lossBefore;
        BigDecimal after = before.add(weight);
        if (after.compareTo(BigDecimal.ONE) > 0) {
            after = BigDecimal.ONE;
        }
        return after.setScale(RATIO_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 损耗费 = 押金 × 本次损耗值
     *
     * @param deposit 押金
     * @param weight  本次损耗值
     * @return 损耗费（两位小数）
     */
    public static BigDecimal lossFee(BigDecimal deposit, BigDecimal weight) {
        return deposit.multiply(weight).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 应退押金 = max(押金 − 逾期费 − 损耗费, 0)
     *
     * @param deposit    押金
     * @param overdueFee 逾期费
     * @param lossFee    损耗费
     * @return 应退押金（两位小数，不为负）
     */
    public static BigDecimal refund(BigDecimal deposit, BigDecimal overdueFee, BigDecimal lossFee) {
        BigDecimal refund = deposit.subtract(overdueFee).subtract(lossFee);
        if (refund.compareTo(BigDecimal.ZERO) < 0) {
            refund = BigDecimal.ZERO;
        }
        return refund.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 累计损耗对应的处置动作：≥ repair 阈值 → REPAIR，≥ maintain 阈值 → MAINTAIN，否则 NONE
     *
     * @param lossAfter 归还后累计损耗
     * @param policy    损耗策略
     * @return 处置动作
     */
    public static String actionOf(BigDecimal lossAfter, LossPolicy policy) {
        if (lossAfter.compareTo(policy.getThresholdRepair()) >= 0) {
            return "REPAIR";
        }
        if (lossAfter.compareTo(policy.getThresholdMaintain()) >= 0) {
            return "MAINTAIN";
        }
        return "NONE";
    }

    /**
     * 把 0–1 的损耗值转成百分比文本（只用于日志与展示）
     *
     * @param ratio 损耗值
     * @return 例如 "0.03%" 这种文本
     */
    public static String percent(BigDecimal ratio) {
        if (ratio == null) {
            return "0%";
        }
        return ratio.multiply(HUNDRED).stripTrailingZeros().toPlainString() + "%";
    }
}
