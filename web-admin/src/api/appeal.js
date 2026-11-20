import request from './request'

/**
 * 管理端申诉接口（契约见 docs/接口文档.md §5.6 / §5.7）。
 *
 * 申诉由订单双方在结算冷静期内提出（承租方多半争「损耗不该算」，出租方多争「损耗算少了」），
 * 所以裁决结果是**四档裁定**而不是一个布尔：
 *   RENTER_WIN 支持承租方 → 损耗费归零
 *   OWNER_WIN  支持出租方 → 按原损耗率恢复损耗费
 *   PARTIAL    部分支持   → 管理员直接给损耗费金额（0 ≤ x ≤ 原损耗费）
 *   REJECTED   驳回       → 金额不动，结算单按原样结清
 *
 * @param {{status?: string, pageNum?: number, pageSize?: number}} params
 */
export function listAppeals(params) {
  return request.get('/admin/appeals', { params })
}

/**
 * 提交裁决。
 *
 * @param {string|number} id 申诉 id
 * @param {{
 *   verdictType: 'RENTER_WIN'|'OWNER_WIN'|'PARTIAL'|'REJECTED',
 *   lossFeeOverride?: number|null,  // 仅 PARTIAL 需要，且不得超过原损耗费
 *   creditAdjust?: number|null,     // 可选 -50~50，作用于承租方，走 ADMIN_ADJUST 流水
 *   verdict: string                 // 裁决说明，≥5 字，双方可见
 * }} data
 */
export function verdictAppeal(id, data) {
  return request.post(`/admin/appeals/${id}/verdict`, data)
}

/** 管理端手动触发冷静期超时扫描（定时任务之外的兜底，答辩演示用） */
export function scanSettlementConfirm() {
  return request.post('/admin/settlements/scan-confirm')
}
