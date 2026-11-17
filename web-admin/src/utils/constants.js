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