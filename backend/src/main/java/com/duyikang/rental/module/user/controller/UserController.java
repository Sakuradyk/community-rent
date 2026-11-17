package com.duyikang.rental.module.user.controller;

import com.duyikang.rental.common.result.Result;
import com.duyikang.rental.module.user.dto.UpdateProfileDTO;
import com.duyikang.rental.module.user.service.UserService;
import com.duyikang.rental.module.user.vo.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前用户资料接口。
 *
 * <p>路径固定为 {@code /api/users/me}：不提供 {@code /api/users/{id}}，
 * 从路由层面就堵死"改 id 看别人资料"的越权，而不是靠代码里判断
 * （PROJECT_RULES.md §7.3）。</p>
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** 当前登录用户信息（含信誉分、社区）。 */
    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.ok(userService.me());
    }

    /** 修改当前登录用户资料（昵称 / 头像 / 社区，传什么改什么）。 */
    @PutMapping("/me")
    public Result<UserVO> updateProfile(@Valid @RequestBody UpdateProfileDTO dto) {
        return Result.ok(userService.updateProfile(dto));
    }
}
