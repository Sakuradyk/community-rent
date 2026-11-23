package com.duyikang.rental.module.admin.controller;

import com.duyikang.rental.common.result.Result;
import com.duyikang.rental.module.system.service.ConfigService;
import com.duyikang.rental.module.system.vo.AdminConfigVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 参数配置（ADMIN 专属）。
 *
 * <p>为什么改完不需要"刷新"接口：业务侧每次读库、不缓存，保存即生效
 * （见 {@link ConfigService} 的类注释与 {@code PROJECT_RULES.md} §9）。</p>
 */
@RestController
@RequestMapping("/api/admin/config")
@RequiredArgsConstructor
public class AdminConfigController {

    private final ConfigService configService;

    @GetMapping
    public Result<AdminConfigVO> load() {
        return Result.ok(configService.load());
    }

    /**
     * @param type {@code credit / fee / loss}
     * @param body 只提交要改的字段，其余保持原值
     */
    @PutMapping("/policy/{type}")
    public Result<Object> updatePolicy(@PathVariable String type, @RequestBody Map<String, Object> body) {
        return Result.ok(configService.updatePolicy(type, body));
    }

    /**
     * @param key   已存在的配置键
     * @param body  {@code {"value": "..."}}
     */
    @PutMapping("/sys/{key}")
    public Result<AdminConfigVO.SysItem> updateSys(@PathVariable String key,
                                                   @RequestBody Map<String, Object> body) {
        Object value = body == null ? null : body.get("value");
        return Result.ok(configService.updateSys(key, value == null ? null : String.valueOf(value)));
    }
}
