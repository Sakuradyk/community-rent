import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as authApi from '@/api/auth'
import { STORAGE_KEY, ROLE } from '@/utils/constants'
import { roleOf } from '@/utils/token'

export const useAdminStore = defineStore('admin', () => {
  const accessToken = ref(localStorage.getItem(STORAGE_KEY.ACCESS) || '')
  const profile = ref(null)

  function save(loginVO) {
    accessToken.value = loginVO.accessToken
    localStorage.setItem(STORAGE_KEY.ACCESS, loginVO.accessToken)
    localStorage.setItem(STORAGE_KEY.REFRESH, loginVO.refreshToken || '')
    if (loginVO.user) {
      localStorage.setItem(STORAGE_KEY.PROFILE, JSON.stringify(loginVO.user))
      profile.value = loginVO.user
    }
  }

  async function login(payload) {
    const vo = await authApi.login(payload)
    // 规则 F3：这里只是不把普通用户放进管理端界面，真正的权限由后端 hasRole('ADMIN') 校验
    if (roleOf(vo.accessToken) !== ROLE.ADMIN) {
      const err = new Error('该账号没有管理员权限')
      err.code = 'NOT_ADMIN'
      throw err
    }
    save(vo)
    return vo
  }

  async function fetchMe() {
    profile.value = await authApi.getMe()
    return profile.value
  }

  async function logout() {
    try {
      await authApi.logout()
    } finally {
      accessToken.value = ''
      profile.value = null
      localStorage.removeItem(STORAGE_KEY.ACCESS)
      localStorage.removeItem(STORAGE_KEY.REFRESH)
      localStorage.removeItem(STORAGE_KEY.PROFILE)
    }
  }

  return { accessToken, profile, login, fetchMe, logout }
})