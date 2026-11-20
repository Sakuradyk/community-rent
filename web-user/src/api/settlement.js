import request from './request'

/**
 * 结算与申诉相关接口（契约见 docs/接口文档.md §5）。
 *
 * request.js 的响应拦截已解包到 data，这里拿到的就是业务载荷本身。
 */

/** 结算单详情（订单双方或管理员可见）。找不到结算单返回 3010 */
export function getSettlement(orderId) {
  return request.get(`/settlements/${orderId}`)
}

/** 承租方点「无异议」：PENDING_CONFIRM → SETTLED，并给双方各记一次按时履约 */
export function confirmSettlement(id) {
  return request.post(`/settlements/${id}/confirm`)
}

/** 承租方提异议：PENDING_CONFIRM → DISPUTED，同时生成一条待处理申诉 */
export function disputeSettlement(id, data) {
  return request.post(`/settlements/${id}/dispute`, data)
}
