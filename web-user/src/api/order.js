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
