import request from './request'

/**
 * 参数配置读写（切片 6，见 docs/接口文档.md §6.4–§6.6）。
 *
 * 三条约定（PROJECT_RULES.md §8.7 / §9）：
 * 1. 后端每次都读库、不缓存 —— 保存成功即生效，前端**不需要**再调"刷新"接口；
 * 2. 策略表只提交要改的字段，未提交的保持原值（不要整表回填，避免把别人刚改的值覆盖回去）；
 * 3. sys_config 只能改已存在的 key，键名由后端白名单把关，前端下拉不要自己造新键。
 */

/** 读：三张策略表 + sys_config 全量。 */
export function getConfig() {
  return request.get('/admin/config')
}

/**
 * 改一张策略表。
 *
 * @param {'credit'|'fee'|'loss'} type
 * @param {object} body 只包含要改的字段
 */
export function updatePolicy(type, body) {
  return request.put(`/admin/config/policy/${type}`, body)
}

/**
 * 改一个 sys_config 键。
 *
 * @param {string} key 已存在的键（如 recommend.w.cf）
 * @param {string} value 新值（后端按 valueType 校验 INT/DECIMAL/BOOLEAN/CRON）
 */
export function updateSysConfig(key, value) {
  return request.put(`/admin/config/sys/${key}`, { value })
}
