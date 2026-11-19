package com.duyikang.rental.job;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.duyikang.rental.module.order.service.OrderStateService;
import com.duyikang.rental.module.system.entity.SysConfig;
import com.duyikang.rental.module.system.mapper.SysConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 逾期扫描定时任务。
 *
 * <p><b>为什么不用 {@code @Scheduled(cron = "...")}：</b>那个注解的 cron 只能来自配置文件，
 * 而本项目要求"策略值来自配置表"（{@code PROJECT_RULES.md} §9，`sys_config` 里已有
 * {@code order.scan.cron}）。这里实现 {@link SchedulingConfigurer}，每次排下一次执行时
 * 现读 {@code sys_config}，所以改库里的 cron 不需要重新编译，下一次调度就生效。</p>
 *
 * <p>真正的业务只有一句：{@code orderStateService.scanOverdue()}，与管理端
 * "立即执行逾期扫描"按钮共用 —— 演示点按钮等于手动跑一次，不会出现两套逻辑。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderScanJob implements SchedulingConfigurer {

    private static final String CRON_KEY = "order.scan.cron";
    /** 兜底 cron：每小时整点（与 02-data.sql 的种子值一致） */
    private static final String DEFAULT_CRON = "0 0 * * * ?";

    private final SysConfigMapper sysConfigMapper;
    private final OrderStateService orderStateService;

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addTriggerTask(this::runOnce, triggerContext ->
                new CronTrigger(resolveCron()).nextExecution(triggerContext));
    }

    private void runOnce() {
        try {
            int updated = orderStateService.scanOverdue();
            if (updated > 0) {
                log.info("定时任务：{} 单转为 OVERDUE（cron={}）", updated, resolveCron());
            }
        } catch (Exception e) {
            // 定时任务不许把异常抛出去：一次失败不能让后续调度停摆
            log.error("定时逾期扫描失败", e);
        }
    }

    /**
     * 读 {@code sys_config['order.scan.cron']}；缺失、为空或不是合法 cron 时回退 {@link #DEFAULT_CRON}
     * 并打 warn —— 配置写错不该让服务起不来，但必须在日志里留痕。
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
