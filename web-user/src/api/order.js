import request from './request'

/**
 * 订单相关接口（契约见 docs/接口文档.md §3）。
 *
 * request.js 的响应拦截已经解包到 `data`，这里拿到的就是业务载荷本身。
 */

/** 费用试算：不写库。冲突时 reject 完整响应体（code 4090，data 里有 conflictDates / suggestRanges） */
export function quoteOrder(data) {
  return request.post('/orders/quote', data)
}

/** 创建订单：服务端重算金额；成功即 PENDING 并立即占时段 */
export function createOrder(data) {
  return request.post('/orders', data)
}

/** 订单详情：仅出租方 / 承租方 / 管理员可见 */
export function getOrder(id) {
  return request.get(`/orders/${id}`)
}

/**
 * 我的订单列表。
 *
 * @param {{role?: 'renter'|'owner'|'all', status?: string, pageNum?: number, pageSize?: number}} params
 */
export function listOrders(params) {
  return request.get('/orders', { params })
}

// ---------------------------------------------------------------- 切片 4：状态流转（契约 §4）

/** 出租方确认出租：PENDING → CONFIRMED */
export function confirmOrder(id) {
  return request.post(`/orders/${id}/confirm`)
}

/** 出租方标记已取用：CONFIRMED → IN_USE */
export function pickupOrder(id) {
  return request.post(`/orders/${id}/pickup`)
}

/** 取消订单：PENDING / CONFIRMED → CANCELLED（后端会释放占用） */
export function cancelOrder(id, data) {
  return request.post(`/orders/${id}/cancel`, data)
}

// ------------------------------------------------- 切片 5：归还（契约 §5.1）

/**
 * 出租方确认归还：IN_USE / OVERDUE → COMPLETED。
 *
 * 后端会一并算逾期费与损耗费、生成结算单（进入冷静期 PENDING_CONFIRM）、
 * 释放占用、给双方记信誉。返回结算单详情，前端直接跳结算页。
 *
 * @param {string|number} id 订单 id
 * @param {{returnState: 'INTACT'|'WORN'|'DAMAGED', remark?: string}} data
 */
export function returnOrder(id, data) {
  return request.post(`/orders/${id}/return`, data)
}
