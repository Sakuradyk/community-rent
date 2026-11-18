// 管理端只保留自己用得到的枚举；与用户端 constants.js 保持一致，改动需同步后端
export const STORAGE_KEY = {
  ACCESS: 'rental_admin_access_token',
  REFRESH: 'rental_admin_refresh_token'
}

export const CODE = {
  SUCCESS: 0,
  UNAUTHORIZED: 1001,
  FORBIDDEN: 1002,
  TOKEN_INVALID: 1003,
  TOKEN_EXPIRED: 1004,
  OCCUPANCY_CONFLICT: 4090
}

export const AUTH_ERROR_CODES = [
  CODE.UNAUTHORIZED,
  CODE.TOKEN_INVALID,
  CODE.TOKEN_EXPIRED
]

export const ROLE = { USER: 'USER', ADMIN: 'ADMIN' }

/** 物品六态（与用户端逐字一致；color 是唯一颜色来源） */
export const ITEM_STATUS = {
  PENDING: { label: '待审核', tag: 'warning', color: '#b45309' },
  ON_SHELF: { label: '已上架', tag: 'success', color: '#15803d' },
  OFF_SHELF: { label: '已下架', tag: 'info', color: '#57534e' },
  RENTED: { label: '出租中', tag: 'primary', color: '#1d4ed8' },
  MAINTAINING: { label: '维修中', tag: 'warning', color: '#b45309' },
  REJECTED: { label: '已驳回', tag: 'danger', color: '#b91c1c' }
}

/** 状态取展示信息，未知值兜底 */
export function statusOf(dict, value) {
  return dict[value] || { label: value || '未知', tag: 'info', color: '#57534e' }
}
