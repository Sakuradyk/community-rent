import axios from 'axios'
import { ElMessage } from 'element-plus'
import { CODE, AUTH_ERROR_CODES, STORAGE_KEY } from '@/utils/constants'
import router from '@/router'

const instance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api',
  timeout: 15000
})

instance.interceptors.request.use((config) => {
  const token = localStorage.getItem(STORAGE_KEY.ACCESS)
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

instance.interceptors.response.use(
  (response) => {
    const body = response.data
    if (!body || typeof body.code === 'undefined') return body
    if (body.code === CODE.SUCCESS) return body.data
    if (AUTH_ERROR_CODES.includes(body.code)) {
      localStorage.removeItem(STORAGE_KEY.ACCESS)
      localStorage.removeItem(STORAGE_KEY.REFRESH)
      if (router.currentRoute.value.name !== 'login') {
        router.replace({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
      }
      ElMessage.error(body.message || '登录状态已失效')
      return Promise.reject(body)
    }
    ElMessage.error(body.message || '请求失败')
    return Promise.reject(body)
  },
  (error) => {
    if (!error?.response) ElMessage.error('网络异常，请检查后端服务是否已启动')
    else ElMessage.error(`请求失败（HTTP ${error.response.status}）`)
    return Promise.reject(error)
  }
)

export default instance