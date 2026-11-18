import request from './request'

/**
 * 管理端物品接口（契约见 docs/接口文档.md §2.9 / §2.10）。
 *
 * request.js 已在 code === 0 时把 data 解出来，所以直接拿业务载荷。
 */

/** 审核列表：不传 status 时后端默认只看 PENDING（待审核） */
export function listAdminItems(params) {
  return request.get('/admin/items', { params })
}

/**
 * 审核：approved=true 通过并上架，false 驳回且 remark 必填。
 * 只有 PENDING 状态的物品能被审核，否则后端返回 3003。
 */
export function auditItem(id, data) {
  return request.post(`/admin/items/${id}/audit`, data)
}
