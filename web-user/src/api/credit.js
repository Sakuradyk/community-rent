import request from './request'

/**
 * 信誉分接口（契约见 docs/接口文档.md §5.5）。
 *
 * @param {{pageNum?: number, pageSize?: number}} params
 */
export function listCredits(params) {
  return request.get('/credits', { params })
}
