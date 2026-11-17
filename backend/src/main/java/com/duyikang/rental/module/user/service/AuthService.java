package com.duyikang.rental.module.user.service;

import com.duyikang.rental.module.user.dto.LoginDTO;
import com.duyikang.rental.module.user.dto.RefreshDTO;
import com.duyikang.rental.module.user.dto.RegisterDTO;
import com.duyikang.rental.module.user.dto.ResetPasswordDTO;
import com.duyikang.rental.module.user.vo.LoginVO;

/**
 * 认证服务：注册、登录、刷新、登出。
 *
 * <p>四个方法都以「发/废令牌」为中心，故放在一起；用户资料维护在
 * {@link UserService}，两者边界是「令牌」与「资料」。</p>
 */
public interface AuthService {

    /**
     * 注册并直接登录。
     *
     * @throws com.duyikang.rental.common.exception.BizException 手机号已注册(2001)、社区不存在(2001)
     */
    LoginVO register(RegisterDTO dto);

    /**
     * 手机号 + 密码登录。
     *
     * @throws com.duyikang.rental.common.exception.BizException 账号或密码错误(1005)、账号被禁用(1006)
     */
    LoginVO login(LoginDTO dto);

    /**
     * 用 refresh 令牌换一对新令牌（旧 refresh 立即失效）。
     *
     * @throws com.duyikang.rental.common.exception.BizException 令牌非法/已失效(1003)、已过期(1004)、账号被禁用(1006)
     */
    LoginVO refresh(RefreshDTO dto);

    /** 登出：删除服务端白名单记录，之后旧 refresh 令牌无法再换新令牌。 */
    void logout();

    /**
     * 忘记密码：知识校验通过后重设密码，并作废该账号所有已签发的 refresh 令牌。
     *
     * <p>校验要素：手机号 + 昵称，若该账号设置过社区还需选对社区。
     * 三项不匹配统一返回同一个 2001 文案，避免把"手机号是否存在"暴露出去。</p>
     *
     * @throws com.duyikang.rental.common.exception.BizException 要素不匹配(2001)、账号被禁用(1006)
     */
    void resetPassword(ResetPasswordDTO dto);
}
