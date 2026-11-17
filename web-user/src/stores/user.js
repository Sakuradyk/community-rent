import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as authApi from '@/api/auth'
import * as userApi from '@/api/user'
import { STORAGE_KEY } from '@/utils/constants'

/**
 * 只放跨页面共享的状态（规则 F10）：令牌 + 当前用户资料。
 * 局部状态（表单、列表）留在组件里。
 */
export const useUserStore = defineStore('user', () => {
  const accessToken = ref(localStorage.getItem(STORAGE_KEY.ACCESS) || '')
  const refreshToken = ref(localStorage.getItem(STORAGE_KEY.REFRESH) || '')
  const profile = ref(readProfile())

  const isLogin = computed(() => !!accessToken.value)

  function readProfile() {
    try {
      const raw = localStorage.getItem(STORAGE_KEY.PROFILE)
      return raw ? JSON.parse(raw) : null
    } catch (e) {
      return null
    }
  }

  function applyLogin(loginVO) {
    accessToken.value = loginVO.accessToken || ''
    refreshToken.value = loginVO.refreshToken || ''
    profile.value = loginVO.user || null
    localStorage.setItem(STORAGE_KEY.ACCESS, accessToken.value)
    localStorage.setItem(STORAGE_KEY.REFRESH, refreshToken.value)
    localStorage.setItem(STORAGE_KEY.PROFILE, JSON.stringify(profile.value))
  }

  function clear() {
    accessToken.value = ''
    refreshToken.value = ''
    profile.value = null
    localStorage.removeItem(STORAGE_KEY.ACCESS)
    localStorage.removeItem(STORAGE_KEY.REFRESH)
    localStorage.removeItem(STORAGE_KEY.PROFILE)
  }

  async function login(payload) {
    applyLogin(await authApi.login(payload))
  }

  async function register(payload) {
    applyLogin(await authApi.register(payload))
  }

  async function fetchMe() {
    profile.value = await userApi.getMe()
    localStorage.setItem(STORAGE_KEY.PROFILE, JSON.stringify(profile.value))
    return profile.value
  }

  async function updateProfile(payload) {
    await userApi.updateMe(payload)
    return fetchMe()
  }

  async function logout() {
    try {
      await authApi.logout()
    } finally {
      clear()
    }
  }

  return {
    accessToken, refreshToken, profile, isLogin,
    login, register, fetchMe, updateProfile, logout, clear
  }
})