import request from './request'

/** 我的钱包：可用 / 冻结 / 总额 */
export function getWallet() {
  return request.get('/wallet')
}

/** 模拟充值（演示用途，无支付渠道，点确认即到账） */
export function recharge(amount) {
  return request.post('/wallet/recharge', { amount })
}

/** 我的资金流水（分页） */
export function listWalletEntries(params) {
  return request.get('/wallet/entries', { params })
}
