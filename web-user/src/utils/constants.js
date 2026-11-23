// 规则 F2：枚举的唯一来源。取值必须与后端 Java 枚举 / 数据库列注释逐字一致。
// 修改本文件 = 修改对外契约，必须同步 docs/接口文档.md 与后端代码。

/** 令牌在 localStorage 中的键（与 stores/user.js 共用，禁止在别处手写字符串） */
export const STORAGE_KEY = {
  ACCESS: 'rental_access_token',
  REFRESH: 'rental_refresh_token',
  PROFILE: 'rental_profile',
  REMEMBER: 'rental_remember'
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

/** 订单六状态（母版口径，禁止增删）
 *  color 是唯一颜色来源：与 assets/styles/variables.scss 的 $color-* 状态色一致。
 *  页面一律 `<el-tag :color="statusOf(ORDER_STATUS, row.status).color" effect="plain">`，
 *  禁止再写 <el-tag type="success"> 这类魔法值，否则同一状态在两个页面会是两种颜色。
 */
export const ORDER_STATUS = {
  PENDING: { label: '待确认', tag: 'warning', color: '#b45309' },
  CONFIRMED: { label: '已确认', tag: 'primary', color: '#1d4ed8' },
  IN_USE: { label: '使用中', tag: 'primary', color: '#15803d' },
  OVERDUE: { label: '已逾期', tag: 'danger', color: '#b91c1c' },
  COMPLETED: { label: '已完成', tag: 'success', color: '#57534e' },
  CANCELLED: { label: '已取消', tag: 'info', color: '#a8a29e' }
}

/** 物品状态 */
export const ITEM_STATUS = {
  PENDING: { label: '待审核', tag: 'warning', color: '#b45309' },
  ON_SHELF: { label: '已上架', tag: 'success', color: '#15803d' },
  OFF_SHELF: { label: '已下架', tag: 'info', color: '#57534e' },
  RENTED: { label: '出租中', tag: 'primary', color: '#1d4ed8' },
  MAINTAINING: { label: '维修中', tag: 'warning', color: '#b45309' },
  REJECTED: { label: '已驳回', tag: 'danger', color: '#b91c1c' }
}

/** 物品维护状态（详情页展示，来自 ItemDetailVO.maintenanceState） */
export const MAINTENANCE_STATE = {
  NORMAL: { label: '状态良好', tag: 'success', color: '#15803d' },
  MAINTAIN: { label: '需保养', tag: 'warning', color: '#b45309' },
  REPAIR: { label: '需检修', tag: 'danger', color: '#b91c1c' }
}

/** 账号状态 */
export const USER_STATUS = {
  NORMAL: { label: '正常', tag: 'success', color: '#15803d' },
  DISABLED: { label: '已禁用', tag: 'danger', color: '#b91c1c' }
}

/**
 * 结算单状态（切片 5 冷静期四态，与后端 SettlementStatus 一一对应）。
 * PENDING_CONFIRM 是冷静期进行中：承租方要么点无异议，要么提异议，超时由定时任务自动确认。
 */
export const SETTLEMENT_STATUS = {
  PENDING_CONFIRM: { label: '待确认', tag: 'warning', color: '#b45309' },
  SETTLED: { label: '已结算', tag: 'success', color: '#15803d' },
  DISPUTED: { label: '有异议', tag: 'danger', color: '#b91c1c' },
  ADJUSTED: { label: '已调整', tag: 'info', color: '#1d4ed8' }
}

/** 归还状态（承租方归还时物品的成色，直接决定损耗系数） */
export const RETURN_STATE = {
  INTACT: { label: '完好', tag: 'success', color: '#15803d', coef: 1.0 },
  WORN: { label: '轻微磨损', tag: 'warning', color: '#b45309', coef: 1.5 },
  DAMAGED: { label: '损坏', tag: 'danger', color: '#b91c1c', coef: 3.0 }
}

/** 资金流水类型与方向（只做展示，金额一律后端算） */
export const FLOW_TYPE = {
  RENT: { label: '租金', direction: 'IN' },
  DEPOSIT: { label: '押金', direction: 'IN' },
  OVERDUE: { label: '逾期费', direction: 'IN' },
  LOSS: { label: '损耗费', direction: 'IN' },
  REFUND: { label: '退还押金', direction: 'OUT' }
}

/** 物品处置动作（按归还后的累计损耗 L 决定） */
export const LOSS_ACTION = {
  NONE: { label: '无需处置', tag: 'success', color: '#15803d' },
  MAINTAIN: { label: '需保养', tag: 'warning', color: '#b45309' },
  REPAIR: { label: '转检修', tag: 'danger', color: '#b91c1c' }
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
  PASSWORD_MAX: 20,
  // 模拟充值：与后端 sys_config['wallet.recharge.max'] 默认值对齐，仅作前端输入上限（最终以后端为准）
  RECHARGE_MAX: 5000
}

/** 日期格式（规则 F8：前端只传这个格式，禁止 Date 对象与 ISO 串） */
export const DATE_FORMAT = 'YYYY-MM-DD'
export const DATETIME_FORMAT = 'YYYY-MM-DD HH:mm:ss'

/** 手机号正则，与后端 RegisterDTO 的 @Pattern 一致 */
export const PHONE_PATTERN = /^1[3-9]\d{9}$/

/** 状态取展示信息，未知值兜底，避免页面因后端新增状态白屏 */
export function statusOf(dict, value) {
  return dict[value] || { label: value || '未知', tag: 'info', color: '#57534e' }
}

/**
 * 把扁平的类目字典组装成两级结构，供发布页级联选择与列表筛选用。
 *
 * 为什么在前端组：类目最多两级、总共十几行，后端返回扁平数组既省一次接口，
 * 也不会因为"前端要树、后端改 VO"而触发契约变更。
 *
 * @param {Array} categories listCategories() 的返回值
 * @returns {Array<{ id: string, name: string, children: Array }>}
 */
export function groupCategories(categories) {
  const list = Array.isArray(categories) ? categories : []
  const levelOne = list.filter((c) => c.level === 1)
  return levelOne.map((parent) => ({
    id: parent.id,
    name: parent.name,
    children: list.filter((c) => c.level === 2 && c.parentId === parent.id)
  }))
}
/**
 * 站内消息类型字典（与后端 MessageType 一一对应）。
 * 只管展示分组，跳转目标由消息自带的 bizType 决定 —— 两组含义不同，别合并。
 */
export const MESSAGE_TYPE = {
  ORDER: { label: '订单', tag: 'primary', color: '#1d4ed8' },
  CREDIT: { label: '信誉', tag: 'warning', color: '#b45309' },
  MAINTAIN: { label: '检修', tag: 'info', color: '#7a6a5d' },
  SYSTEM: { label: '系统', tag: 'info', color: '#57534e' }
}

/** 消息业务类型 → 详情页路由名（用于点击跳转） */
export const BIZ_ROUTE = {
  ORDER: 'order-detail',
  SETTLEMENT: 'settlement',
  ITEM: 'item-detail'
}


/**
 * 充值快捷金额（演示用）：点一下填进输入框，用户仍可自定义。
 */
export const RECHARGE_PRESETS = [50, 100, 200, 500]
