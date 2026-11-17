import request from './request'

/**
 * 上传图片（需登录）。返回 { url, size }，url 形如 /uploads/202611/xxx.png
 * 用 FormData 交给 axios 自动带 boundary；不手写 Content-Type。
 */
export function uploadImage(file) {
  const form = new FormData()
  form.append('file', file)
  return request.post('/files/upload', form)
}