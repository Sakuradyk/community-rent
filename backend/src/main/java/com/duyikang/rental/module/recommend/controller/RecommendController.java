package com.duyikang.rental.module.recommend.controller;

import com.duyikang.rental.common.result.Result;
import com.duyikang.rental.module.recommend.service.RecommendService;
import com.duyikang.rental.module.recommend.vo.RecommendResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 推荐接口（M5）。
 *
 * <p>匿名可访问（首页未登录也要能推荐），带 token 时由 {@code CurrentUser} 拿到个性化偏好。</p>
 */
@RestController
@RequestMapping("/api/recommend")
@RequiredArgsConstructor
public class RecommendController {

    private final RecommendService recommendService;

    /**
     * @param limit 条数上限，默认 6、上限 20（越界 2001）
     */
    @GetMapping
    public Result<RecommendResultVO> recommend(@RequestParam(required = false) Integer limit) {
        return Result.ok(recommendService.recommend(limit));
    }
}
