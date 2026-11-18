package com.duyikang.rental.module.item.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.duyikang.rental.module.item.entity.Category;
import com.duyikang.rental.module.item.mapper.CategoryMapper;
import com.duyikang.rental.module.item.service.CategoryService;
import com.duyikang.rental.module.item.vo.CategoryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 类目字典服务实现。
 *
 * <p>刻意"一次查全量、不提供按父类目查询的子接口"：本项目类目只有 11 行，
 * 前端拿一次就能同时渲染筛选下拉和发布页两级联动，多一个接口只是多一处契约要维护。</p>
 */
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    /** 启用状态码（与 category.status 的库内取值一致）。 */
    private static final String STATUS_NORMAL = "NORMAL";

    private final CategoryMapper categoryMapper;

    @Override
    public List<CategoryVO> listEnabled() {
        List<Category> categories = categoryMapper.selectList(Wrappers.<Category>lambdaQuery()
                .eq(Category::getStatus, STATUS_NORMAL)
                .orderByAsc(Category::getLevel)
                .orderByAsc(Category::getSortNo)
                .orderByAsc(Category::getId));
        return categories.stream()
                .map(category -> new CategoryVO(
                        category.getId(),
                        category.getName(),
                        category.getParentId(),
                        category.getLevel()))
                .toList();
    }
}
