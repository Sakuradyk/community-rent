import { STORAGE_KEY } from './constants'

/**
 * 「记住密码」的本地存储。
 *
 * <p><b>这不是加密，只是 base64 混淆</b>：localStorage 对同源脚本完全可读，
 * 任何 XSS 都能拿走。用 base64 仅避免明文密码在 DevTools 里一眼看到（防肩窥、防截图），
 * 真正的安全边界靠"只存在这台电脑的浏览器里 + 随时可取消勾选清除"。
 * 真实项目应改为后端下发长期令牌，本项目按需求总结 §10 不做令牌长期化。</p>
 */

export function saveRemember(phone, password) {
  const payload = JSON.stringify({ phone, password })
  // btoa 只吃 Latin-1，中文/特殊字符必须先 encodeURIComponent 转成 ASCII
  localStorage.setItem(STORAGE_KEY.REMEMBER, window.btoa(encodeURIComponent(payload)))
}

export function loadRemember() {
  const raw = localStorage.getItem(STORAGE_KEY.REMEMBER)
  if (!raw) return null
  try {
    const parsed = JSON.parse(decodeURIComponent(window.atob(raw)))
    if (!parsed || typeof parsed.phone !== 'string' || typeof parsed.password !== 'string') {
      return null
    }
    return parsed
  } catch (e) {
    // 数据被改坏就丢弃，绝不让登录页因为一条脏存储而白屏
    localStorage.removeItem(STORAGE_KEY.REMEMBER)
    return null
  }
}

export function clearRemember() {
  localStorage.removeItem(STORAGE_KEY.REMEMBER)
}
