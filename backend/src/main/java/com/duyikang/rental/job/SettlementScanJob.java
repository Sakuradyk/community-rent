package com.duyikang.rental.job;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.duyikang.rental.module.settlement.service.SettlementService;
import com.duyikang.rental.module.system.entity.SysConfig;
import com.duyikang.rental.module.system.mapper.SysConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

/**
 * 结算冷静期扫描定时任务（K3）。
 *
 * <p>冷静期是「承租方 48 小时内不表态就默认接受」，所以必须有一条自动收尾的腿：
 * 归还是出租方单方面认定损耗，承租方超时未异议即视为认可，管理员裁决才有意义（否则结算单永远挂着）。</p>
 *
 * <p>cron 来自 {@code sys_config['settlement.scan.cron']}，改库即生效；
 * 与管理端「立即执行结算扫描」按钮调同一个方法 {@code settlementService.scanConfirmTimeout()}。</p>
 *
 * <p>同一个 tick 里还跑第二条腿 {@link SettlementService#scanReturnTimeout()}：
 * 承租方发起归还申请后出租方超窗口未确认 → 按承租方申报状态自动结算（两者都幂等，共用一个 cron 即可）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementScanJob implements SchedulingConfigurer {

    private static final String CRON_KEY = "settlement.scan.cron";

    /** 兜底 cron：每小时第 5 分钟（与 02-data.sql 的种子值一致，错开逾期扫描） */
    private static final String DEFAULT_CRON = "0 5 * * * ?";

    private final SysConfigMapper sysConfigMapper;

    private final SettlementService settlementService;

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addTriggerTask(this::runOnce, triggerContext ->
                new CronTrigger(resolveCron()).nextExecution(triggerContext));
    }

    private void runOnce() {
        // 冷静期超时：PENDING_CONFIRM → SETTLED
        try {
            int updated = settlementService.scanConfirmTimeout();
            if (updated > 0) {
                log.info("定时任务：{} 条结算单冷静期超时自动确认（cron={}）", updated, resolveCron());
            }
        } catch (Exception e) {
            // 定时任务不许把异常抛出去：一次失败不能让后续调度停摆
            log.error("定时结算冷静期扫描失败", e);
        }
        // 归还申请超时：承租方已申报、出租方超窗口未确认 → 按承租方申报状态自动结算
        try {
            int auto = settlementService.scanReturnTimeout();
            if (auto > 0) {
                log.info("定时任务：{} 单归还申请超过窗口未确认，已按承租方申报自动结算", auto);
            }
        } catch (Exception e) {
            log.error("定时归还申请超时扫描失败", e);
        }
    }

    /**
     * 读 {@code sys_config['settlement.scan.cron']}；缺失、为空或非法时回退 {@link #DEFAULT_CRON} 并留痕
     */
    private String resolveCron() {
        try {
            SysConfig config = sysConfigMapper.selectOne(
                    Wrappers.<SysConfig>lambdaQuery().eq(SysConfig::getConfigKey, CRON_KEY));
            String cron = config == null ? null : config.getConfigValue();
            if (cron == null || cron.isBlank()) {
                return DEFAULT_CRON;
            }
            String trimmed = cron.trim();
            if (!CronExpression.isValidExpression(trimmed)) {
                log.warn("sys_config['{}'] 不是合法 cron（{}），回退 {}", CRON_KEY, trimmed, DEFAULT_CRON);
                return DEFAULT_CRON;
            }
            return trimmed;
        } catch (Exception e) {
            log.warn("读取 sys_config['{}'] 失败，回退 {}：{}", CRON_KEY, DEFAULT_CRON, e.getMessage());
            return DEFAULT_CRON;
        }
    }
}
