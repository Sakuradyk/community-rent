package com.duyikang.rental.security;

import com.duyikang.rental.common.constant.ErrorCode;
import com.duyikang.rental.common.exception.BizException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * 从 {@code SecurityContextHolder} 读取当前登录主体。
 *
 * <p>为什么要有这个类：物品列表/详情是 {@code permitAll} 的，但"登录了就按我的社区优先排序"，
 * 所以既需要"取不到也不报错"（{@link #optional()}），也需要"取不到就 401"（{@link #requireId()}）。
 * 而当前用户一律取自令牌主体，**绝不信任请求参数里的 userId**（PROJECT_RULES.md §7.3）。</p>
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    /** 匿名可访问的接口用这个：没有登录态返回 {@link Optional#empty()}。 */
    public static Optional<LoginUser> optional() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser loginUser)) {
            return Optional.empty();
        }
        return Optional.of(loginUser);
    }

    /** 必须登录的接口用这个：取不到直接 401。 */
    public static LoginUser require() {
        return optional().orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED));
    }

    /** 取当前用户 id，取不到 401。 */
    public static Long requireId() {
        return require().userId();
    }

    /** 当前用户是否管理员（部分"本人或管理员可见"的规则要用）。 */
    public static boolean isAdmin() {
        return optional().map(user -> "ADMIN".equals(user.role())).orElse(false);
    }
}
