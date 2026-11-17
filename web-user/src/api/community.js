import request from './request'

/** 社区字典（匿名可读）→ [{ id, name, district }] */
export function listCommunities() {
  return request.get('/communities')
}