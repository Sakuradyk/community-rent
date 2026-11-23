package com.duyikang.rental.module.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.duyikang.rental.common.constant.ErrorCode;
import com.duyikang.rental.common.exception.BizException;
import com.duyikang.rental.module.credit.entity.CreditPolicy;
import com.duyikang.rental.module.credit.mapper.CreditPolicyMapper;
import com.duyikang.rental.module.loss.entity.LossPolicy;
import com.duyikang.rental.module.loss.mapper.LossPolicyMapper;
import com.duyikang.rental.module.settlement.entity.FeePolicy;
import com.duyikang.rental.module.settlement.mapper.FeePolicyMapper;
import com.duyikang.rental.module.system.entity.SysConfig;
import com.duyikang.rental.module.system.mapper.SysConfigMapper;
import com.duyikang.rental.module.system.service.ConfigService;
import com.duyikang.rental.module.system.vo.AdminConfigVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 参数配置实现。
 *
 * <p>三条硬规则：①只改白名单字段（body 里多传的键直接忽略，不报错也不落库）；
 * ②改完做跨字段一致性校验（`threshold_repair > threshold_maintain` 之类），越界抛 2001；
 * ③不做缓存 —— 业务侧每次都读库，所以"保存即生效"不需要额外刷新动作。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConfigServiceImpl implements ConfigService {

    private static final Integer POLICY_ID = 1;
    private static final String TYPE_CREDIT = "credit";
    private static final String TYPE_FEE = "fee";
    private static final String TYPE_LOSS = "loss";

    private static final String VALUE_INT = "INT";
    private static final String VALUE_DECIMAL = "DECIMAL";
    private static final String VALUE_BOOLEAN = "BOOLEAN";
    private static final String VALUE_CRON = "CRON";

    /**
     * INT 型参数的取值区间（左闭右闭）。不在表里的 INT 键只做"是不是整数"的校验。
     * 这些区间是**业务约束**，不是随手写的：例如权重必须落在 [0,1]，否则加权分数会失真。
     */
    private static final Map<String, int[]> INT_RANGES = Map.ofEntries(
            Map.entry("recommend.top_n", new int[]{1, 20}),
            Map.entry("recommend.decay.half_life", new int[]{1, 365}),
            Map.entry("recommend.heat.window", new int[]{1, 365}),
            Map.entry("community.priority.enabled", new int[]{0, 1}),
            Map.entry("order.lock.ttl", new int[]{1, 60}),
            Map.entry("order.max_days", new int[]{1, 365}),
            Map.entry("order.return.confirm.hours", new int[]{0, 168}),
            Map.entry("settlement.confirm.hours", new int[]{1, 168}));

    /** 权重类参数必须落在 [0,1] —— 三个权重加起来最好等于 1，但不强制（归一化由算法侧保证）。 */
    private static final Set<String> WEIGHT_KEYS = Set.of(
            "recommend.w.cf", "recommend.w.content", "recommend.w.heat");

    private final CreditPolicyMapper creditPolicyMapper;
    private final FeePolicyMapper feePolicyMapper;
    private final LossPolicyMapper lossPolicyMapper;
    private final SysConfigMapper sysConfigMapper;

    @Override
    public AdminConfigVO load() {
        List<SysConfig> rows = sysConfigMapper.selectList(Wrappers.<SysConfig>lambdaQuery()
                .orderByAsc(SysConfig::getConfigGroup)
                .orderByAsc(SysConfig::getConfigKey));
        List<AdminConfigVO.SysItem> items = new ArrayList<>(rows.size());
        for (SysConfig row : rows) {
            items.add(toItem(row));
        }
        return new AdminConfigVO(
                creditPolicyMapper.selectById(POLICY_ID),
                feePolicyMapper.selectById(POLICY_ID),
                lossPolicyMapper.selectById(POLICY_ID),
                items);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Object updatePolicy(String type, Map<String, Object> body) {
        String normalized = type == null ? "" : type.trim().toLowerCase();
        return switch (normalized) {
            case TYPE_CREDIT -> updateCredit(body);
            case TYPE_FEE -> updateFee(body);
            case TYPE_LOSS -> updateLoss(body);
            default -> throw new BizException(ErrorCode.PARAM_ERROR,
                    "未知的策略类型：" + type + "（只支持 credit / fee / loss）");
        };
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminConfigVO.SysItem updateSys(String key, String value) {
        SysConfig row = sysConfigMapper.selectOne(Wrappers.<SysConfig>lambdaQuery()
                .eq(SysConfig::getConfigKey, key));
        if (row == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "未知参数：" + key + "（只能修改已存在的参数）");
        }
        String checked = validateValue(key, row.getValueType(), value);
        row.setConfigValue(checked);
        sysConfigMapper.updateById(row);
        log.info("参数配置修改：{} = {}（原 {}）", key, checked, row.getConfigValue());
        return toItem(row);
    }

    // ---------------------------------------------------------------- 三张策略表

    private CreditPolicy updateCredit(Map<String, Object> body) {
        CreditPolicy policy = creditPolicyMapper.selectById(POLICY_ID);
        if (policy == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "信誉策略未初始化（credit_policy 缺少 id=1）");
        }
        applyInt(body, "initScore", policy::setInitScore);
        applyInt(body, "minScore", policy::setMinScore);
        applyInt(body, "maxScore", policy::setMaxScore);
        applyInt(body, "rewardOnTime", policy::setRewardOnTime);
        applyInt(body, "rewardDailyCap", policy::setRewardDailyCap);
        applyInt(body, "penaltyOverdueLight", policy::setPenaltyOverdueLight);
        applyInt(body, "penaltyOverdueHeavy", policy::setPenaltyOverdueHeavy);
        applyInt(body, "penaltyDamage", policy::setPenaltyDamage);
        applyInt(body, "penaltyCancelAfterConfirm", policy::setPenaltyCancelAfterConfirm);
        applyInt(body, "tierFreeDepositMin", policy::setTierFreeDepositMin);
        applyInt(body, "tierNormalMin", policy::setTierNormalMin);
        applyInt(body, "tierNormalMax", policy::setTierNormalMax);
        applyInt(body, "tierDepositUpMin", policy::setTierDepositUpMin);
        applyInt(body, "tierDepositUpMax", policy::setTierDepositUpMax);
        applyDecimal(body, "tierDepositUpRatio", policy::setTierDepositUpRatio);
        applyInt(body, "tierSuspendBelow", policy::setTierSuspendBelow);

        // 区间 [0,100] 是母版口径，越界的分数会让 clamp 失效
        if (policy.getMinScore() > policy.getMaxScore()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "minScore 不能大于 maxScore");
        }
        if (policy.getInitScore() < policy.getMinScore() || policy.getInitScore() > policy.getMaxScore()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "initScore 必须落在 [minScore, maxScore] 之内");
        }
        if (policy.getRewardOnTime() < 0 || policy.getRewardDailyCap() < 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "奖励分值必须是正数");
        }
        if (policy.getPenaltyOverdueLight() > 0 || policy.getPenaltyOverdueHeavy() > 0
                || policy.getPenaltyDamage() > 0 || policy.getPenaltyCancelAfterConfirm() > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "扣分项应存负数（例如 -5）");
        }
        if (policy.getTierNormalMin() > policy.getTierNormalMax()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "tierNormalMin 不能大于 tierNormalMax");
        }
        if (policy.getTierNormalMax() >= policy.getTierFreeDepositMin()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "正常档上限必须小于免押门槛");
        }
        if (policy.getTierDepositUpMin() >= policy.getTierDepositUpMax()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "加成档下限必须小于上限");
        }
        if (policy.getTierDepositUpRatio() != null && policy.getTierDepositUpRatio().signum() <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "押金加成倍数必须大于 0");
        }
        creditPolicyMapper.updateById(policy);
        log.info("信誉策略已更新：{}", policy);
        return policy;
    }

    private FeePolicy updateFee(Map<String, Object> body) {
        FeePolicy policy = feePolicyMapper.selectById(POLICY_ID);
        if (policy == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "费用策略未初始化（fee_policy 缺少 id=1）");
        }
        applyInt(body, "overdueTier1Days", policy::setOverdueTier1Days);
        applyDecimal(body, "overdueTier1Rate", policy::setOverdueTier1Rate);
        applyDecimal(body, "overdueTier2Rate", policy::setOverdueTier2Rate);
        applyDecimal(body, "overdueRateMin", policy::setOverdueRateMin);
        applyDecimal(body, "overdueRateMax", policy::setOverdueRateMax);
        applyDecimal(body, "dailyPriceMin", policy::setDailyPriceMin);
        applyDecimal(body, "dailyPriceMax", policy::setDailyPriceMax);
        applyDecimal(body, "depositRatioMin", policy::setDepositRatioMin);
        applyDecimal(body, "depositRatioMax", policy::setDepositRatioMax);
        applyInt(body, "cancelFreeHours", policy::setCancelFreeHours);

        if (policy.getOverdueTier1Days() < 1) {
            throw new BizException(ErrorCode.PARAM_ERROR, "逾期第一档天数必须 ≥ 1");
        }
        if (policy.getOverdueTier1Rate().signum() <= 0 || policy.getOverdueTier2Rate().signum() <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "逾期费率必须大于 0");
        }
        if (policy.getOverdueRateMin().compareTo(policy.getOverdueRateMax()) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "overdueRateMin 不能大于 overdueRateMax");
        }
        if (policy.getDailyPriceMin().compareTo(policy.getDailyPriceMax()) >= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "dailyPriceMin 必须小于 dailyPriceMax");
        }
        if (policy.getDepositRatioMin().compareTo(policy.getDepositRatioMax()) >= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "depositRatioMin 必须小于 depositRatioMax");
        }
        if (policy.getCancelFreeHours() < 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "免费取消小时数不能为负");
        }
        feePolicyMapper.updateById(policy);
        log.info("费用策略已更新：{}", policy);
        return policy;
    }

    private LossPolicy updateLoss(Map<String, Object> body) {
        LossPolicy policy = lossPolicyMapper.selectById(POLICY_ID);
        if (policy == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "损耗策略未初始化（loss_policy 缺少 id=1）");
        }
        applyDecimal(body, "thresholdMaintain", policy::setThresholdMaintain);
        applyDecimal(body, "thresholdRepair", policy::setThresholdRepair);
        applyInt(body, "termTier1MaxDays", policy::setTermTier1MaxDays);
        applyDecimal(body, "termTier1Coef", policy::setTermTier1Coef);
        applyInt(body, "termTier2MaxDays", policy::setTermTier2MaxDays);
        applyDecimal(body, "termTier2Coef", policy::setTermTier2Coef);
        applyDecimal(body, "termTier3Coef", policy::setTermTier3Coef);
        applyDecimal(body, "returnCoefIntact", policy::setReturnCoefIntact);
        applyDecimal(body, "returnCoefWorn", policy::setReturnCoefWorn);
        applyDecimal(body, "returnCoefDamaged", policy::setReturnCoefDamaged);

        if (policy.getThresholdMaintain().compareTo(policy.getThresholdRepair()) >= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "保养阈值必须小于检修阈值");
        }
        if (policy.getThresholdRepair().compareTo(BigDecimal.ONE) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "检修阈值不能大于 1");
        }
        if (policy.getTermTier1MaxDays() >= policy.getTermTier2MaxDays()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "租期第一档天数上限必须小于第二档");
        }
        if (policy.getReturnCoefIntact().signum() <= 0
                || policy.getReturnCoefIntact().compareTo(policy.getReturnCoefWorn()) > 0
                || policy.getReturnCoefWorn().compareTo(policy.getReturnCoefDamaged()) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "归还状态系数必须满足 完好 ≤ 轻微磨损 ≤ 损坏，且均大于 0");
        }
        lossPolicyMapper.updateById(policy);
        log.info("损耗策略已更新：{}", policy);
        return policy;
    }

    // ---------------------------------------------------------------- 取值与校验

    private void applyInt(Map<String, Object> body, String key, Consumer<Integer> setter) {
        if (body == null || !body.containsKey(key) || body.get(key) == null) {
            return;
        }
        Object raw = body.get(key);
        try {
            setter.accept(new BigDecimal(String.valueOf(raw).trim()).intValueExact());
        } catch (ArithmeticException | NumberFormatException e) {
            throw new BizException(ErrorCode.PARAM_ERROR, key + " 必须是整数");
        }
    }

    private void applyDecimal(Map<String, Object> body, String key, Consumer<BigDecimal> setter) {
        if (body == null || !body.containsKey(key) || body.get(key) == null) {
            return;
        }
        try {
            setter.accept(new BigDecimal(String.valueOf(body.get(key)).trim()));
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.PARAM_ERROR, key + " 必须是数字");
        }
    }

    private String validateValue(String key, String valueType, String value) {
        if (value == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "参数值不能为空");
        }
        String trimmed = value.trim();
        String type = valueType == null ? VALUE_INT : valueType.toUpperCase();
        switch (type) {
            case VALUE_INT -> {
                int parsed;
                try {
                    parsed = new BigDecimal(trimmed).intValueExact();
                } catch (ArithmeticException | NumberFormatException e) {
                    throw new BizException(ErrorCode.PARAM_ERROR, key + " 必须是整数");
                }
                int[] range = INT_RANGES.get(key);
                if (range != null && (parsed < range[0] || parsed > range[1])) {
                    throw new BizException(ErrorCode.PARAM_ERROR,
                            key + " 应落在 " + range[0] + "~" + range[1] + " 之间");
                }
                return String.valueOf(parsed);
            }
            case VALUE_DECIMAL -> {
                BigDecimal parsed;
                try {
                    parsed = new BigDecimal(trimmed);
                } catch (NumberFormatException e) {
                    throw new BizException(ErrorCode.PARAM_ERROR, key + " 必须是数字");
                }
                if (WEIGHT_KEYS.contains(key)
                        && (parsed.signum() < 0 || parsed.compareTo(BigDecimal.ONE) > 0)) {
                    throw new BizException(ErrorCode.PARAM_ERROR, key + " 是权重，应落在 0~1 之间");
                }
                if (parsed.signum() < 0) {
                    throw new BizException(ErrorCode.PARAM_ERROR, key + " 不能为负数");
                }
                return parsed.stripTrailingZeros().toPlainString();
            }
            case VALUE_BOOLEAN -> {
                String lower = trimmed.toLowerCase();
                if ("1".equals(lower) || "0".equals(lower)) {
                    return lower;
                }
                if ("true".equals(lower)) {
                    return "1";
                }
                if ("false".equals(lower)) {
                    return "0";
                }
                throw new BizException(ErrorCode.PARAM_ERROR, key + " 只能是 0/1 或 true/false");
            }
            case VALUE_CRON -> {
                if (!CronExpression.isValidExpression(trimmed)) {
                    throw new BizException(ErrorCode.PARAM_ERROR, key + " 不是合法的 cron 表达式（六段：秒 分 时 日 月 周）");
                }
                return trimmed;
            }
            default -> throw new BizException(ErrorCode.PARAM_ERROR, "不支持的参数类型：" + valueType);
        }
    }

    private AdminConfigVO.SysItem toItem(SysConfig row) {
        return new AdminConfigVO.SysItem(
                row.getConfigKey(),
                row.getConfigValue(),
                row.getValueType(),
                row.getConfigGroup(),
                row.getDescription());
    }

    /** 预留：前端可能按分组渲染，保留分组顺序的稳定映射能力。 */
    @SuppressWarnings("unused")
    private Map<String, List<AdminConfigVO.SysItem>> group(List<AdminConfigVO.SysItem> items) {
        Map<String, List<AdminConfigVO.SysItem>> grouped = new LinkedHashMap<>();
        for (AdminConfigVO.SysItem item : items) {
            grouped.computeIfAbsent(item.group(), k -> new ArrayList<>()).add(item);
        }
        return grouped;
    }
}
