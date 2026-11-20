import request from './request'

/**
 * 管理端申诉接口（契约见 docs/接口文档.md §5.6 / §5.7）。
 *
 * 申诉由承租方在结算冷静期内提出，裁决结果直接改写结算单：
 * 通过 → 免损耗费并重算应退（结算单 ADJUSTED）；驳回 → 按原金额结清（SETTLED）。
 *
 * @param {{status?: string, pageNum?: number, pageSize?: number}} params
 */
export function listAppeals(params) {
  return request.get('/admin/appeals', { params })
}

/** @param {{accepted: boolean, verdict: string}} data */
export function verdictAppeal(id, data) {
  return request.post(`/admin/appeals/${id}/verdict`, data)
}

/** 管理端手动触发冷静期超时扫描（定时任务之外的兜底，答辩演示用） */
export function scanSettlementConfirm() {
  return request.post('/admin/settlements/scan-confirm')
}
