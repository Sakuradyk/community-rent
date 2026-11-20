package com.duyikang.rental.module.credit.controller;

import com.duyikang.rental.common.result.PageResult;
import com.duyikang.rental.common.result.Result;
import com.duyikang.rental.module.credit.service.CreditService;
import com.duyikang.rental.module.credit.vo.CreditLogVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 信誉流水接口（K4）。只读：信誉分只能由业务动作（归还、逾期、损坏、取消）推动，不接受直接改分。
 */
@RestController
@RequestMapping("/api/credits")
@RequiredArgsConstructor
public class CreditController {

    private final CreditService creditService;

    /**
     * 我的信誉流水
     *
     * @param pageNum  页码，从 1 起
     * @param pageSize 每页条数，默认 10，上限 100
     * @return 分页结果
     */
    @GetMapping
    public Result<PageResult<CreditLogVO>> myLogs(
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize) {
        return Result.ok(creditService.myLogs(pageNum, pageSize));
    }
}
