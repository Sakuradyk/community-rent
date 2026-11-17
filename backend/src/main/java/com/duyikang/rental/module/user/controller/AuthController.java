package com.duyikang.rental.module.user.controller;

import com.duyikang.rental.common.result.Result;
import com.duyikang.rental.module.user.dto.LoginDTO;
import com.duyikang.rental.module.user.dto.RefreshDTO;
import com.duyikang.rental.module.user.dto.RegisterDTO;
import com.duyikang.rental.module.user.dto.ResetPasswordDTO;
import com.duyikang.rental.module.user.service.AuthService;
import com.duyikang.rental.module.user.vo.LoginVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口（注册 / 登录 / 刷新 / 登出 / 忘记密码）。
 *
 * <p>Controller 只做三件事：接参、交给 Service、包成 {@link Result}；
 * 业务规则一律在 Service 里，不写 if 判断业务（PROJECT_RULES.md §4.3）。</p>
 *
 * <p>登录、注册、刷新、忘记密码在 {@code SecurityConfig} 里放行；登出需要带 access 令牌。</p>
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 注册并直接返回令牌对。 */
    @PostMapping("/register")
    public Result<LoginVO> register(@Valid @RequestBody RegisterDTO dto) {
        return Result.ok(authService.register(dto));
    }

    /** 手机号 + 密码登录。 */
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.ok(authService.login(dto));
    }

    /** 用 refresh 令牌换一对新令牌（旧 refresh 立即作废）。 */
    @PostMapping("/refresh")
    public Result<LoginVO> refresh(@Valid @RequestBody RefreshDTO dto) {
        return Result.ok(authService.refresh(dto));
    }

    /** 登出：无需入参，当前用户从令牌主体取。 */
    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout();
        return Result.ok();
    }

    /** 忘记密码：手机号 + 昵称（+ 注册时选的社区）校验通过后重设密码，并作废该账号全部会话。 */
    @PostMapping("/reset-password")
    public Result<Void> resetPassword(@Valid @RequestBody ResetPasswordDTO dto) {
        authService.resetPassword(dto);
        return Result.ok();
    }
}
