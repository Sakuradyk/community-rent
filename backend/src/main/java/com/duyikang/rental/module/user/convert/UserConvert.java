package com.duyikang.rental.module.user.convert;

import com.duyikang.rental.module.user.entity.User;
import com.duyikang.rental.module.user.vo.UserVO;

/**
 * 用户实体 → 视图对象的手写转换。
 *
 * <p>为什么手写不引 MapStruct：一个转换类不值得多一个注解处理器依赖，
 * 且脱敏这种业务逻辑用注解表达反而更绕（PROJECT_RULES.md §2.1 禁新依赖）。</p>
 */
public final class UserConvert {

    private UserConvert() {
    }

    /**
     * 转 VO 并脱敏手机号。
     *
     * <p>脱敏规则：保留前 3 后 4（138****0001）。运营能看到用户是谁，
     * 但即使日志/截图外泄也拿不到完整号码（PROJECT_RULES.md §10）。</p>
     */
    public static UserVO toVO(User user) {
        if (user == null) {
            return null;
        }
        return new UserVO(
                user.getId(),
                user.getNickname(),
                maskPhone(user.getPhone()),
                user.getAvatarUrl(),
                user.getCommunityId(),
                user.getCreditScore(),
                user.getStatus(),
                user.getCreatedAt()
        );
    }

    /** 手机号脱敏：不足 7 位时整串打星，避免越界。 */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return "****";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
