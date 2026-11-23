package com.duyikang.rental.module.recommend.vo;

import java.math.BigDecimal;
import java.util.List;

/**
 * 推荐结果（{@code GET /api/recommend} 的 data）。
 *
 * <p>`weights` 把本次实际用的三个权重回显给前端 —— 管理端在参数配置页改过权重之后，
 * 前端不用猜"现在到底是不是 0.6/0.3/0.1"，直接把页面上显示的权重换成这份数据即可。</p>
 *
 * @param limit     本次请求的条数上限
 * @param coldStart true 表示走了冷启动兜底（匿名，或该用户还没有任何订单）
 * @param weights   本次使用的权重
 * @param list      推荐结果，已按 score 降序
 */
public record RecommendResultVO(Integer limit, boolean coldStart, WeightsVO weights, List<RecommendItemVO> list) {

    /** 三个权重（全部来自 {@code sys_config}，改配置即改推荐）。 */
    public record WeightsVO(BigDecimal cf, BigDecimal content, BigDecimal heat) {
    }
}
