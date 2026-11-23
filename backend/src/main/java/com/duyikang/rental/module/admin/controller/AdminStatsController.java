package com.duyikang.rental.module.admin.controller;

import com.duyikang.rental.common.result.Result;
import com.duyikang.rental.module.admin.service.StatsService;
import com.duyikang.rental.module.admin.vo.StatsOverviewVO;
import com.duyikang.rental.module.admin.vo.StatsTrendVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端看板（ADMIN 专属，路径落在 {@code /api/admin/**} 由 SecurityConfig 兜底鉴权）。
 */
@RestController
@RequestMapping("/api/admin/stats")
@RequiredArgsConstructor
public class AdminStatsController {

    private final StatsService statsService;

    @GetMapping("/overview")
    public Result<StatsOverviewVO> overview() {
        return Result.ok(statsService.overview());
    }

    /**
     * @param days 天数 1–30，默认 7（越界 2001）
     */
    @GetMapping("/trend")
    public Result<StatsTrendVO> trend(@RequestParam(required = false) Integer days) {
        return Result.ok(statsService.trend(days));
    }
}
