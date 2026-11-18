import request from './request'

/**
 * 物品接口（契约见 docs/接口文档.md §2，已冻结 2026-11-18）。
 *
 * 约定：request.js 在 code === 0 时已经把 data 解出来，所以这里每个函数返回的都是业务载荷本身
 * （列表就是 { list, total, pageNum, pageSize }，不是 { code, data: {...} }）。
 */

/** 列表（匿名可读；带上 startDate/endDate 时每张卡会带 available 字段） */
export function listItems(params) {
  return request.get('/items', { params })
}

/** 详情（匿名可读；PENDING/REJECTED 只有归属人和管理员能看到，否则后端返回 3004） */
export function getItem(id) {
  return request.get(`/items/${id}`)
}

/** 占用日历（匿名可读；month 传 YYYY-MM，后端把整月补齐成 occupiedDates/availableDates） */
export function getItemCalendar(id, month) {
  return request.get(`/items/${id}/calendar`, { params: { month } })
}

/** 发布（登录；落 PENDING 待审核，返回 { id }，注意 id 是字符串） */
export function createItem(data) {
  return request.post('/items', data)
}

/** 编辑（仅归属人；只有 PENDING/OFF_SHELF/REJECTED 可编辑，改完回到 PENDING） */
export function updateItem(id, data) {
  return request.put(`/items/${id}`, data)
}

/** 上架/下架（在架物品有进行中订单时不允许下架，后端返回 3003） */
export function shelfItem(id, onShelf) {
  return request.put(`/items/${id}/shelf`, { onShelf })
}

/** 我的物品（登录；带 status 可筛选，返回 statusLabel 与 auditRemark） */
export function listMyItems(params) {
  return request.get('/items/mine', { params })
}

/** 删除（归属人；逻辑删除，已产生订单的物品后端会拒绝） */
export function deleteItem(id) {
  return request.delete(`/items/${id}`)
}
