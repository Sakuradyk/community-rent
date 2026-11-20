package com.duyikang.rental.module.admin.controller;

import com.duyikang.rental.common.result.Result;
import com.duyikang.rental.module.admin.vo.ScanResultVO;
import com.duyikang.rental.module.order.service.OrderStateService;
import com.duyikang.rental.module.settlement.service.SettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 管理端订单接口（切片 4：逾期扫描；切片 5：归还申请超时扫描）。
 *
 * <p>路径以 {@code /api/admin/} 开头，Security 已统一要求 {@code ROLE_ADMIN}（见 {@code SecurityConfig}）。</p>
 *
 * <p>为什么要有这些按钮：定时任务各有 cron（{@code order.scan.cron} / {@code settlement.scan.cron}），
 * 答辩现场不可能等到整点，所以要能手动触发一次 —— 与定时任务调的是同一个方法，
 * 不存在"演示路径"和"真实路径"两套逻辑。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private final OrderStateService orderStateService;

    private final SettlementService settlementService;

    /**
     * 立即执行逾期扫描：把已过归还日但仍"使用中"的订单转为"已逾期"。
     */
    @PostMapping("/scan-overdue")
    public Result<ScanResultVO> scanOverdue() {
        int updated = orderStateService.scanOverdue();
        log.info("管理端手动触发逾期扫描：{} 单转 OVERDUE", updated);
        return Result.ok(new ScanResultVO(updated, LocalDateTime.now()));
    }

    /**
     * 立即执行归还申请超时扫描：承租方已申报归还、出租方超过窗口未确认的订单，按承租方申报状态自动结算。
     */
    @PostMapping("/scan-return")
    public Result<ScanResultVO> scanReturn() {
        int updated = settlementService.scanReturnTimeout();
        log.info("管理端手动触发归还超时扫描：{} 单自动结算", updated);
        return Result.ok(new ScanResultVO(updated, LocalDateTime.now()));
    }
}
