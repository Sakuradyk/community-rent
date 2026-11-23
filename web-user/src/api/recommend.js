import request from './request'

/**
 * 混合推荐（M5）。
 *
 * 匿名访问也会返回结果（后端走冷启动纯热度榜，`coldStart=true`）；
 * 带 token 时后端用「我的历史订单 + 我的物品类目」算个性化分数。
 *
 * 返回结构见 docs/接口文档.md §6.1：
 * `{ limit, coldStart, weights: { cf, content, heat }, list: [...] }`
 * 每条都带 `reasonCode`（CF / CONTENT / HEAT / COLD_START）与后端拼好的 `reason` 文案 ——
 * **前端不许自己解释推荐理由**，否则同一件事两处文案会漂移（PROJECT_RULES.md §8.7）。
 *
 * @param {number} [limit=6] 条数，1–20
 */
export function getRecommend(limit = 6) {
  return request.get('/recommend', { params: { limit } })
}
