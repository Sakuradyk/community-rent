package com.duyikang.rental.module.admin.controller;

import com.duyikang.rental.common.result.PageResult;
import com.duyikang.rental.common.result.Result;
import com.duyikang.rental.module.admin.vo.ScanResultVO;
import com.duyikang.rental.module.appeal.dto.AppealVerdictDTO;
import com.duyikang.rental.module.appeal.service.AppealService;
import com.duyikang.rental.module.appeal.vo.AppealItemVO;
import com.duyikang.rental.module.settlement.service.SettlementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 管理端结算与申诉接口（K3）。
 *
 * <p>`scan-confirm` 与定时任务调同一个方法：答辩演示时点一下就能把超时未表态的结算单转成已结算，
 * 不用等 48 小时。</p>
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminSettlementController {

    private final SettlementService settlementService;

    private final AppealService appealService;

    /**
     * 申诉列表
     *
     * @param status   状态过滤，默认 PENDING
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    @GetMapping("/appeals")
    public Result<PageResult<AppealItemVO>> appeals(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize) {
        return Result.ok(appealService.page(status, pageNum, pageSize));
    }

    /**
     * 裁决一条申诉
     *
     * @param id  申诉 id
     * @param dto 裁决结果
     * @return 空响应
     */
    @PostMapping("/appeals/{id}/verdict")
    public Result<Void> verdict(@PathVariable Long id, @Valid @RequestBody AppealVerdictDTO dto) {
        appealService.verdict(id, dto);
        return Result.ok();
    }

    /**
     * 立即执行结算冷静期扫描
     *
     * @return 本次处理条数与扫描时间
     */
    @PostMapping("/settlements/scan-confirm")
    public Result<ScanResultVO> scanConfirm() {
        int updated = settlementService.scanConfirmTimeout();
        return Result.ok(new ScanResultVO(updated, LocalDateTime.now()));
    }
}
