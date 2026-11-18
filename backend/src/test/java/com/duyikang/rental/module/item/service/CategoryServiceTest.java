package com.duyikang.rental.module.item.service;

import com.duyikang.rental.module.item.vo.CategoryVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 类目字典：发布页/筛选下拉的数据源必须稳定。
 */
@SpringBootTest
@Transactional
class CategoryServiceTest {

    @Autowired
    private CategoryService categoryService;

    @Test
    @DisplayName("listEnabled：返回 11 个启用类目，一级 5 个、二级 6 个，且按层级与排序值升序")
    void listEnabled_returnsAllEnabledCategoriesOrderedByLevelAndSortNo() {
        List<CategoryVO> categories = categoryService.listEnabled();

        assertThat(categories).hasSize(11);
        assertThat(categories).filteredOn(c -> c.level() == 1).hasSize(5);
        assertThat(categories).filteredOn(c -> c.level() == 2).hasSize(6);

        // 一级在前（level 升序），一级内部按 sort_no（10/20/30/40/50）排序
        assertThat(categories.get(0).name()).isEqualTo("电子数码");
        assertThat(categories.get(0).parentId()).isZero();
        assertThat(categories.get(4).name()).isEqualTo("家居生活");

        // 二级类目的 parentId 必须指向真实的一级类目（前端靠它组织两级下拉）
        List<Long> levelOneIds = categories.stream()
                .filter(c -> c.level() == 1)
                .map(CategoryVO::id)
                .toList();
        assertThat(categories.stream().filter(c -> c.level() == 2).map(CategoryVO::parentId))
                .allMatch(levelOneIds::contains);

        CategoryVO drill = categories.stream()
                .filter(c -> "电钻".equals(c.name()))
                .findFirst()
                .orElseThrow();
        assertThat(drill.level()).isEqualTo(2);
        assertThat(drill.parentId()).isEqualTo(3L);
    }
}
