import axios from 'axios'
import { ElMessage } from 'element-plus'
import { CODE, AUTH_ERROR_CODES, STORAGE_KEY } from '@/utils/constants'
import router from '@/router'

/**
 * 规则 F7：axios 单实例，统一处理 业务码 / 401 类 / 4090。
 * 约定：成功时 resolve 出 data.data（业务载荷），失败时 reject 出统一响应体本身，
 * 让调用方还能拿到 code 与 data（4090 的 conflictDates / suggestRanges 就在这里）。
 */
const instance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api',
  timeout: 15000
})

instance.interceptors.request.use((config) => {
  const token = localStorage.getItem(STORAGE_KEY.ACCESS)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

function clearAuthAndGoLogin() {
  localStorage.removeItem(STORAGE_KEY.ACCESS)
  localStorage.removeItem(STORAGE_KEY.REFRESH)
  localStorage.removeItem(STORAGE_KEY.PROFILE)
  const current = router.currentRoute.value
  if (current.name !== 'login') {
    router.replace({ name: 'login', query: { redirect: current.fullPath } })
  }
}

instance.interceptors.response.use(
  (response) => {
    const body = response.data
    // 后端统一响应体 { code, message, data, traceId }
    if (!body || typeof body.code === 'undefined') {
      return body
    }
    if (body.code === CODE.SUCCESS) {
      return body.data
    }
    if (AUTH_ERROR_CODES.includes(body.code)) {
      clearAuthAndGoLogin()
      ElMessage.error(body.message || '登录状态已失效，请重新登录')
      return Promise.reject(body)
    }
    // 4090 不弹全局提示：由下单页面自己在日历上标红并给出建议区间
    if (body.code !== CODE.OCCUPANCY_CONFLICT) {
      ElMessage.error(body.message || '请求失败')
    }
    return Promise.reject(body)
  },
  (error) => {
    const status = error?.response?.status
    if (status === 401) {
      clearAuthAndGoLogin()
      ElMessage.error('登录状态已失效，请重新登录')
    } else if (error?.code === 'ECONNABORTED') {
      ElMessage.error('请求超时，请稍后重试')
    } else if (!error?.response) {
      ElMessage.error('网络异常，请检查后端服务是否已启动')
    } else {
      ElMessage.error(`请求失败（HTTP ${status}）`)
    }
    return Promise.reject(error)
  }
)

export default instance