package com.duyikang.rental.module.admin.controller;

import com.duyikang.rental.common.result.Result;
import com.duyikang.rental.module.admin.vo.ScanResultVO;
import com.duyikang.rental.module.order.service.OrderStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 管理端订单接口（切片 4：逾期扫描）。
 *
 * <p>路径以 {@code /api/admin/} 开头，Security 已统一要求 {@code ROLE_ADMIN}（见 {@code SecurityConfig}）。</p>
 *
 * <p>为什么要有这个按钮：定时任务是 {@code order.scan.cron}（默认每小时整点），
 * 答辩现场不可能等到整点，所以要能手动触发一次 —— 它与定时任务调的是同一个方法，
 * 不存在"演示路径"和"真实路径"两套逻辑。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private final OrderStateService orderStateService;

    /**
     * 立即执行逾期扫描：把已过归还日但仍"使用中"的订单转为"已逾期"。
     */
    @PostMapping("/scan-overdue")
    public Result<ScanResultVO> scanOverdue() {
        int updated = orderStateService.scanOverdue();
        log.info("管理端手动触发逾期扫描：{} 单转 OVERDUE", updated);
        return Result.ok(new ScanResultVO(updated, LocalDateTime.now()));
    }
}
