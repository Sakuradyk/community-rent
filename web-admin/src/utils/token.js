/**
 * 只读解析 JWT 载荷，用于前端体验层判断角色（规则 F3：真正的权限在后端）。
 * 不验签——验签只有后端能做，这里解开只是为了不把普通用户放进管理端界面。
 */
export function parseJwtPayload(token) {
  if (!token) return null
  const parts = token.split('.')
  if (parts.length !== 3) return null
  try {
    const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/')
    const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4)
    const json = decodeURIComponent(
      atob(padded).split('').map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2)).join('')
    )
    return JSON.parse(json)
  } catch (e) {
    return null
  }
}

export function roleOf(token) {
  return parseJwtPayload(token)?.role || ''
}