import request from './request'

/**
 * 类目字典（匿名可读）。
 *
 * 返回扁平结构：[{ id, name, parentId, level }]，level 1 为一级、2 为二级。
 * 发布物品只能选二级类目；列表筛选可以传一级（后端自动展开成它的二级子类目）。
 */
export function listCategories() {
  return request.get('/categories')
}
