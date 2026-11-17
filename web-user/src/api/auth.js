import request from './request'

/** 注册：{ phone, password, nickname, communityId } → LoginVO */
export function register(data) {
  return request.post('/auth/register', data)
}

/** 登录：{ phone, password } → LoginVO */
export function login(data) {
  return request.post('/auth/login', data)
}

/** 刷新令牌：{ refreshToken } → LoginVO（旧 refresh 立即失效） */
export function refreshToken(data) {
  return request.post('/auth/refresh', data)
}

/** 登出：作废服务端 refresh 白名单 */
export function logout() {
  return request.post('/auth/logout')
}