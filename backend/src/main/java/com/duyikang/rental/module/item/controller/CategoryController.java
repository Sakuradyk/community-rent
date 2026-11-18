package com.duyikang.rental.module.item.controller;

import com.duyikang.rental.common.result.Result;
import com.duyikang.rental.module.item.service.CategoryService;
import com.duyikang.rental.module.item.vo.CategoryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 类目字典接口（匿名可读：注册不受影响，但发布页与筛选下拉都依赖它）。
 *
 * <p>之所以放在切片 2 才补：契约 §2.4 的 {@code categoryId} 筛选与 §2.1 的发布入参都要求
 * "只能选二级类目"，而此前项目里没有任何接口能把类目字典交给前端，属于冻结契约时的遗漏。</p>
 */
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    /**
     * 列出启用中的类目（一级 + 二级，扁平结构，前端按 parentId 组两级下拉）。
     *
     * @return 类目列表；停用的类目不会出现
     */
    @GetMapping
    public Result<List<CategoryVO>> list() {
        return Result.ok(categoryService.listEnabled());
    }
}
