import dayjs from 'dayjs'
import { DATE_FORMAT, DATETIME_FORMAT } from './constants'

/**
 * 金额只做展示（规则 F1：前端不算钱）。
 * 后端 BigDecimal 序列化成数字时可能丢尾零，这里统一补到两位。
 */
export function formatMoney(value) {
  const n = Number(value)
  if (!Number.isFinite(n)) return '0.00'
  return n.toFixed(2)
}

export function formatDate(value) {
  if (!value) return '-'
  return dayjs(value).format(DATE_FORMAT)
}

export function formatDateTime(value) {
  if (!value) return '-'
  return dayjs(value).format(DATETIME_FORMAT)
}

/** 手机号脱敏：139****2001（后端 UserVO 已脱敏，这里是兜底显示） */
export function maskPhone(phone) {
  if (!phone || phone.length < 7) return '****'
  return phone.slice(0, 3) + '****' + phone.slice(-4)
}

/** 把后端返回的相对路径补成可直接用于 <img src> 的地址（开发期由 Vite proxy 转发 /uploads） */
export function resolveImageUrl(url) {
  if (!url) return ''
  if (/^https?:\/\//i.test(url)) return url
  return url
}