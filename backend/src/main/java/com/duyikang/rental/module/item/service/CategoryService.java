package com.duyikang.rental.module.item.service;

import com.duyikang.rental.module.item.vo.CategoryVO;

import java.util.List;

/**
 * 类目字典服务（只读）。
 *
 * <p>类目是"发布物品"与"列表筛选"共用的字典：发布时必须选一个二级类目，
 * 列表传一级类目时会自动展开成它的二级子类目（见 {@code ItemServiceImpl#resolveCategoryIds}）。</p>
 */
public interface CategoryService {

    /**
     * 列出所有启用中的类目（一级与二级一起，按层级、排序值升序）。
     *
     * @return 类目列表；停用（DISABLED）与已逻辑删除的不会出现
     */
    List<CategoryVO> listEnabled();
}
