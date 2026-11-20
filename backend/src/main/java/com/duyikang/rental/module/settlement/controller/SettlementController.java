package com.duyikang.rental.module.settlement.controller;

import com.duyikang.rental.common.result.Result;
import com.duyikang.rental.module.settlement.dto.SettlementDisputeDTO;
import com.duyikang.rental.module.settlement.service.SettlementService;
import com.duyikang.rental.module.settlement.vo.SettlementDetailVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 结算单接口（K3）：查询、确认无异议、提出异议。归还动作挂在订单下（`POST /api/orders/{id}/return`）。
 */
@RestController
@RequestMapping("/api/settlements")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementService settlementService;

    /**
     * 按订单查结算单（订单双方或管理员）
     *
     * @param orderId 订单 id
     * @return 结算单详情
     */
    @GetMapping("/{orderId}")
    public Result<SettlementDetailVO> detail(@PathVariable Long orderId) {
        return Result.ok(settlementService.detailByOrder(orderId));
    }

    /**
     * 承租方确认无异议
     *
     * @param id 结算单 id
     * @return 空响应
     */
    @PostMapping("/{id}/confirm")
    public Result<Void> confirm(@PathVariable Long id) {
        settlementService.confirm(id);
        return Result.ok();
    }

    /**
     * 承租方提出异议（会生成一条待处理申诉）
     *
     * @param id  结算单 id
     * @param dto 异议内容
     * @return 空响应
     */
    @PostMapping("/{id}/dispute")
    public Result<Void> dispute(@PathVariable Long id, @Valid @RequestBody SettlementDisputeDTO dto) {
        settlementService.dispute(id, dto);
        return Result.ok();
    }
}
