import request from './request'

/** 当前登录用户 → UserVO（手机号已脱敏） */
export function getMe() {
  return request.get('/users/me')
}

/** 修改资料：{ nickname, avatarUrl, communityId }，全部可选 */
export function updateMe(data) {
  return request.put('/users/me', data)
}