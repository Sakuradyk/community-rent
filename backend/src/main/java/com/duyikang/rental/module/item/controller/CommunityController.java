package com.duyikang.rental.module.item.controller;

import com.duyikang.rental.common.result.Result;
import com.duyikang.rental.module.item.service.CommunityService;
import com.duyikang.rental.module.item.vo.CommunityVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 社区字典接口。
 *
 * <p>匿名可访问（SecurityConfig 已放行 {@code /api/communities}）：注册页与发布页
 * 都要先拿到下拉选项，未登录用户也得能读。只读、不含用户数据。</p>
 */
@RestController
@RequestMapping("/api/communities")
public class CommunityController {

    private final CommunityService communityService;

    public CommunityController(CommunityService communityService) {
        this.communityService = communityService;
    }

    /**
     * 查询启用中的社区列表（下拉数据源）。
     *
     * @return 按 sort_no 升序的社区选项，仅含 id / name / district
     */
    @GetMapping
    public Result<List<CommunityVO>> list() {
        return Result.ok(communityService.listEnabled());
    }
}
