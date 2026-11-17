// 规则 F2：枚举的唯一来源。取值必须与后端 Java 枚举 / 数据库列注释逐字一致。
// 修改本文件 = 修改对外契约，必须同步 docs/接口文档.md 与后端代码。

/** 令牌在 localStorage 中的键（与 stores/user.js 共用，禁止在别处手写字符串） */
export const STORAGE_KEY = {
  ACCESS: 'rental_access_token',
  REFRESH: 'rental_refresh_token',
  PROFILE: 'rental_profile'
}

/** 统一响应体里的成功码 */
export const CODE = {
  SUCCESS: 0,
  UNAUTHORIZED: 1001,
  FORBIDDEN: 1002,
  TOKEN_INVALID: 1003,
  TOKEN_EXPIRED: 1004,
  ACCOUNT_OR_PASSWORD_ERROR: 1005,
  ACCOUNT_DISABLED: 1006,
  PARAM_ERROR: 2001,
  FILE_TYPE_NOT_ALLOWED: 2004,
  FILE_TOO_LARGE: 2005,
  ORDER_STATE_INVALID: 3001,
  CREDIT_NOT_ENOUGH: 3002,
  ITEM_STATUS_INVALID: 3003,
  OCCUPANCY_CONFLICT: 4090,
  SYSTEM_ERROR: 5000
}

/** 认证类错误码：拿到即清登录态并跳登录页 */
export const AUTH_ERROR_CODES = [
  CODE.UNAUTHORIZED,
  CODE.TOKEN_INVALID,
  CODE.TOKEN_EXPIRED
]

/** 订单六状态（母版口径，禁止增删） */
export const ORDER_STATUS = {
  PENDING: { label: '待确认', tag: 'warning' },
  CONFIRMED: { label: '已确认', tag: 'primary' },
  IN_USE: { label: '使用中', tag: 'primary' },
  OVERDUE: { label: '已逾期', tag: 'danger' },
  COMPLETED: { label: '已完成', tag: 'success' },
  CANCELLED: { label: '已取消', tag: 'info' }
}

/** 物品状态 */
export const ITEM_STATUS = {
  PENDING: { label: '待审核', tag: 'warning' },
  ON_SHELF: { label: '已上架', tag: 'success' },
  OFF_SHELF: { label: '已下架', tag: 'info' },
  RENTED: { label: '出租中', tag: 'primary' },
  MAINTAINING: { label: '维修中', tag: 'warning' },
  REJECTED: { label: '已驳回', tag: 'danger' }
}

/** 账号状态 */
export const USER_STATUS = {
  NORMAL: { label: '正常', tag: 'success' },
  DISABLED: { label: '已禁用', tag: 'danger' }
}

/** 角色码 */
export const ROLE = {
  USER: 'USER',
  ADMIN: 'ADMIN'
}

/** 分页与上传等硬限制（必须与后端配置一致） */
export const LIMITS = {
  PAGE_SIZE: 10,
  PAGE_SIZE_MAX: 100,
  IMAGE_MAX_MB: 5,
  IMAGE_ALLOWED_EXT: ['jpg', 'jpeg', 'png', 'webp'],
  IMAGE_MAX_COUNT: 5,
  NICKNAME_MIN: 2,
  NICKNAME_MAX: 16,
  PASSWORD_MIN: 6,
  PASSWORD_MAX: 20
}

/** 日期格式（规则 F8：前端只传这个格式，禁止 Date 对象与 ISO 串） */
export const DATE_FORMAT = 'YYYY-MM-DD'
export const DATETIME_FORMAT = 'YYYY-MM-DD HH:mm:ss'

/** 手机号正则，与后端 RegisterDTO 的 @Pattern 一致 */
export const PHONE_PATTERN = /^1[3-9]\d{9}$/

/** 状态取展示信息，未知值兜底，避免页面因后端新增状态白屏 */
export function statusOf(dict, value) {
  return dict[value] || { label: value || '未知', tag: 'info' }
}