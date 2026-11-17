import { STORAGE_KEY } from '@/utils/constants'

/**
 * 规则 F3：前端守卫只做体验（没登录就别让进），真正的权限判定在后端。
 * 后端仍然会对每个接口校验令牌与角色，绕过这里改前端也拿不到数据。
 */
export function setupRouterGuard(router) {
  router.beforeEach((to) => {
    const hasToken = !!localStorage.getItem(STORAGE_KEY.ACCESS)

    if (to.meta.requiresAuth && !hasToken) {
      return { name: 'login', query: { redirect: to.fullPath } }
    }
    // 已登录还去登录/注册页，直接回首页，避免重复登录
    if (hasToken && (to.name === 'login' || to.name === 'register')) {
      return { name: 'home' }
    }
    return true
  })

  router.afterEach((to) => {
    const base = '邻里租'
    document.title = to.meta.title ? `${to.meta.title} · ${base}` : base
  })
}