package com.duyikang.rental.module.recommend.service;

import com.duyikang.rental.module.recommend.vo.RecommendResultVO;

import java.time.LocalDateTime;

/**
 * 混合推荐（M5 / K5）。
 *
 * <p>公式与口径写死在 {@code docs/接口文档.md} §6.0：{@code score = wCf×cf + wContent×content + wHeat×heat}，
 * 三个权重、衰减半衰期、热度窗口、top_n 全部来自 {@code sys_config}，<b>本类内不许出现 0.6/0.3/0.1 字面量</b>。</p>
 */
public interface RecommendService {

    /**
     * 给当前访问者产出推荐列表。
     *
     * @param limit 条数上限（默认 6，上限 20；越界抛 2001）
     * @return 推荐结果（含本次使用的权重与是否冷启动）
     */
    RecommendResultVO recommend(Integer limit);

    /**
     * 指定「历史截止时间」的推荐，<b>只供离线评估使用</b>（见 {@code docs/notes/M5-推荐评估.md}）。
     *
     * @param limit         条数上限
     * @param historyCutoff 只把该时间之前的订单当作历史（null = 用全部历史，等价于 {@link #recommend(Integer)}）
     */
    RecommendResultVO recommend(Integer limit, LocalDateTime historyCutoff);
}
