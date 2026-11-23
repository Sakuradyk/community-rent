import request from './request'

/** 账户列表（只列用户账户，支持按昵称/手机号过滤） */
export function listAccounts(params) {
  return request.get('/admin/accounts', { params })
}

/** 全站分录（支持按 bizType 过滤） */
export function listAccountEntries(params) {
  return request.get('/admin/accounts/entries', { params })
}

/** 人工调账：正数=给用户补款，负数=从用户扣款，必须写备注 */
export function adjustAccount(userId, payload) {
  return request.post(`/admin/accounts/${userId}/adjust`, payload)
}

/** 对账：全站余额之和、外部账户余额、分录数 */
export function getReconcile() {
  return request.get('/admin/accounts/reconcile')
}
