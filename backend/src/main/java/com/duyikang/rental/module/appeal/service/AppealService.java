package com.duyikang.rental.module.appeal.service;

import com.duyikang.rental.common.result.PageResult;
import com.duyikang.rental.module.appeal.dto.AppealVerdictDTO;
import com.duyikang.rental.module.appeal.vo.AppealItemVO;

/**
 * 申诉服务（K3 冷静期的对价手段）。
 *
 * <p>申诉由承租方在提异议时自动生成，管理员裁决后：
 * 通过 → 结算单 `ADJUSTED`（免收损耗费并重算应退押金）；驳回 → 结算单回 `SETTLED`。</p>
 */
public interface AppealService {

    /**
     * 管理端申诉列表（默认只看待处理）
     *
     * @param status   PENDING / ACCEPTED / REJECTED，空则默认 PENDING
     * @param pageNum  页码，从 1 起
     * @param pageSize 每页条数，上限 100
     * @return 分页结果
     */
    PageResult<AppealItemVO> page(String status, Integer pageNum, Integer pageSize);

    /**
     * 裁决一条申诉
     *
     * @param appealId 申诉 id
     * @param dto      裁决结果与说明
     */
    void verdict(Long appealId, AppealVerdictDTO dto);
}
