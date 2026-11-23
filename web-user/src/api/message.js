import request from './request'

/**
 * 站内消息接口。
 *
 * 说明：`request.js` 的响应拦截已经把 `Result.data` 解包出来，
 * 这里拿到的是业务载荷本身（分页对象 / 数字 / null），不用再 `.data`。
 */

/** 我的消息列表（`unreadOnly=true` 只看未读） */
export function listMessages(params) {
  return request.get('/messages', { params })
}

/** 未读条数（顶栏铃铛用，字段是 `{ count }`） */
export function getUnreadCount() {
  return request.get('/messages/unread-count')
}

/** 单条标记已读（幂等） */
export function markMessageRead(id) {
  return request.post(`/messages/${id}/read`)
}

/** 全部标记已读，返回本次更新条数 */
export function markAllMessagesRead() {
  return request.post('/messages/read-all')
}
