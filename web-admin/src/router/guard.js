import { STORAGE_KEY, ROLE } from '@/utils/constants'
import { roleOf } from '@/utils/token'

export function setupRouterGuard(router) {
  router.beforeEach((to) => {
    const token = localStorage.getItem(STORAGE_KEY.ACCESS)
    if (to.meta.requiresAdmin) {
      if (!token) return { name: 'login', query: { redirect: to.fullPath } }
      if (roleOf(token) !== ROLE.ADMIN) {
        localStorage.removeItem(STORAGE_KEY.ACCESS)
        localStorage.removeItem(STORAGE_KEY.REFRESH)
        return { name: 'login', query: { denied: '1' } }
      }
    }
    if (token && to.name === 'login') return { name: 'dashboard' }
    return true
  })

  router.afterEach((to) => {
    document.title = to.meta.title ? `${to.meta.title} · 邻里租管理端` : '邻里租管理端'
  })
}