import request from './request'

/**
 * 管理端看板统计（切片 6，见 docs/接口文档.md §6.2 / §6.3）。
 */

/** 概览：用户数、物品数、六种订单状态计数、待确认结算、待裁决申诉、GMV、平均信誉分。 */
export function getOverview() {
  return request.get('/admin/stats/overview')
}

/**
 * 趋势：按天升序，空天补 0，最后一天是今天（后端补洞，前端直接画）。
 *
 * @param {number} [days=7] 1–30
 */
export function getTrend(days = 7) {
  return request.get('/admin/stats/trend', { params: { days } })
}
