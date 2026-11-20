package com.duyikang.rental.module.credit.service;

import com.duyikang.rental.common.result.PageResult;
import com.duyikang.rental.module.credit.vo.CreditLogVO;

/**
 * 信誉分服务（K4）。
 *
 * <p>三条硬约束，改动这里必须先读：</p>
 * <ol>
 *   <li><b>幂等</b>：`credit_log` 的唯一键是 (user_id, order_id, reason_code)，
 *       同一笔订单的同一原因只记一次分 —— 定时任务重跑、用户连点都不会双倍扣分；</li>
 *   <li><b>封顶</b>：正向变动同一自然日累计不超过 `credit_policy.reward_daily_cap`（默认 +4），
 *       否则刷单可以无限涨分；</li>
 *   <li><b>区间</b>：`after_score = clamp(before + change, min_score, max_score)`，
 *       库里还有 `ck_credit_range` 兜底，越界会直接报数据库错误。</li>
 * </ol>
 */
public interface CreditService {

    /**
     * 记一次信誉变动（幂等，重复调用不会重复记分）
     *
     * @param userId     被调整的用户
     * @param orderId    关联订单（信誉变动必须能追溯到订单）
     * @param reasonCode 原因码：ON_TIME / OVERDUE_LIGHT / OVERDUE_HEAVY / DAMAGE / CANCEL_AFTER_CONFIRM / ADMIN_ADJUST
     * @param change     变动值（可为负）
     * @param reasonDesc 展示给用户看的说明
     */
    void apply(Long userId, Long orderId, String reasonCode, int change, String reasonDesc);

    /**
     * 我的信誉流水（按时间倒序分页）
     *
     * @param pageNum  页码，从 1 起
     * @param pageSize 每页条数，上限 100
     * @return 分页结果
     */
    PageResult<CreditLogVO> myLogs(Integer pageNum, Integer pageSize);
}
