package com.duyikang.rental.module.user;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.duyikang.rental.common.exception.BizException;
import com.duyikang.rental.common.exception.GlobalExceptionHandler;
import com.duyikang.rental.common.result.Result;
import com.duyikang.rental.module.credit.mapper.CreditPolicyMapper;
import com.duyikang.rental.module.user.dto.LoginDTO;
import com.duyikang.rental.module.user.dto.RefreshDTO;
import com.duyikang.rental.module.user.dto.RegisterDTO;
import com.duyikang.rental.module.user.dto.ResetPasswordDTO;
import com.duyikang.rental.module.user.entity.User;
import com.duyikang.rental.module.user.entity.UserRole;
import com.duyikang.rental.module.user.mapper.RoleMapper;
import com.duyikang.rental.module.user.mapper.UserMapper;
import com.duyikang.rental.module.user.mapper.UserRoleMapper;
import com.duyikang.rental.module.user.service.AuthService;
import com.duyikang.rental.module.user.service.UserService;
import com.duyikang.rental.module.user.vo.LoginVO;
import com.duyikang.rental.security.JwtUtil;
import com.duyikang.rental.security.LoginUser;
import com.duyikang.rental.security.RefreshTokenStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 认证链路集成测试（连真实 MySQL + Redis）。
 *
 * <p>为什么用集成测试而不是 Mock：注册要验证"user 与 user_role 同事务"、
 * 登录要验证"读库策略分"、刷新要验证"Redis 白名单"，这些行为都发生在
 * 真实数据库与真实 Redis 上，Mock 掉任何一个都测不到真问题
 * （PROJECT_RULES.md §12）。</p>
 *
 * <p>数据库改动由 {@code @Transactional} 自动回滚；<b>Redis 不受事务管辖</b>，
 * 故在 {@link #cleanRedis()} 里按创建过的 userId 逐个删 key。</p>
 */
@SpringBootTest
@Transactional
class AuthIntegrationTest {

    private static final String PASSWORD = "123456";
    private static final long COMMUNITY_ID = 1L;
    private static final long POLICY_ID = 1L;

    @Autowired
    private AuthService authService;
    @Autowired
    private UserService userService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private UserRoleMapper userRoleMapper;
    @Autowired
    private RoleMapper roleMapper;
    @Autowired
    private CreditPolicyMapper creditPolicyMapper;
    @Autowired
    private RefreshTokenStore refreshTokenStore;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private GlobalExceptionHandler globalExceptionHandler;

    private final List<Long> createdUserIds = new ArrayList<>();

    @AfterEach
    void cleanRedis() {
        createdUserIds.forEach(refreshTokenStore::remove);
        createdUserIds.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("register_合法入参_返回令牌对且初始信誉分取自策略表")
    void register_validInput_returnsTokensAndInitCreditFromPolicy() {
        LoginVO vo = authService.register(new RegisterDTO("13900001001", PASSWORD, "测试甲", COMMUNITY_ID));
        createdUserIds.add(vo.user().id());

        assertNotNull(vo.accessToken());
        assertNotNull(vo.refreshToken());
        assertEquals(7200L, vo.expiresIn(), "access 有效期应为 120 分钟");

        User saved = userMapper.selectById(vo.user().id());
        Integer expectedInitScore = creditPolicyMapper.selectById(POLICY_ID).getInitScore();
        assertEquals(expectedInitScore, saved.getCreditScore(), "初始信誉分必须来自 credit_policy.init_score，不能写死");
        assertEquals("NORMAL", saved.getStatus());
        assertEquals(COMMUNITY_ID, saved.getCommunityId());
        // 密码必须是 BCrypt 密文，绝不能明文落库
        assertNotEquals(PASSWORD, saved.getPasswordHash());
        // 返回的手机号已脱敏
        assertEquals("139****1001", vo.user().phone());
        // 契约回归：注册返回的 UserVO 必须带 createdAt（insert 后要重新读库，否则为 null）
        assertNotNull(vo.user().createdAt(), "注册响应必须返回 createdAt（docs/接口文档.md §1.6）");

        List<UserRole> links = userRoleMapper.selectList(
                Wrappers.<UserRole>lambdaQuery().eq(UserRole::getUserId, saved.getId()));
        assertEquals(1, links.size(), "注册必须同时写入 user_role");
        assertEquals("USER", roleMapper.selectById(links.get(0).getRoleId()).getCode());

        String jti = jwtUtil.parse(vo.refreshToken()).getId();
        assertTrue(refreshTokenStore.matches(saved.getId(), jti), "refresh 令牌必须已写入白名单");
    }

    @Test
    @DisplayName("register_手机号已存在_抛2001而不是4090")
    void register_duplicatePhone_throwsParamError() {
        LoginVO first = authService.register(new RegisterDTO("13900001002", PASSWORD, "测试乙", COMMUNITY_ID));
        createdUserIds.add(first.user().id());

        BizException e = assertThrows(BizException.class,
                () -> authService.register(new RegisterDTO("13900001002", PASSWORD, "测试乙2", COMMUNITY_ID)));
        assertEquals(2001, e.getCode());
        assertEquals("手机号已注册", e.getMessage());
    }

    @Test
    @DisplayName("handleDuplicateKey_按约束名分流_手机号2001时段4090")
    void handleDuplicateKey_mappedByConstraintName() {
        Result<Void> phone = globalExceptionHandler.handleDuplicateKey(
                new DuplicateKeyException("Duplicate entry '13900001002' for key 'user.uk_user_phone'"));
        assertEquals(2001, phone.getCode());
        assertEquals("手机号已注册", phone.getMessage());

        Result<Void> occupancy = globalExceptionHandler.handleDuplicateKey(
                new DuplicateKeyException("Duplicate entry '2-2026-11-20' for key 'order_occupancy.uk_occupancy'"));
        assertEquals(4090, occupancy.getCode());
        assertEquals("该时段已被占用，请选择其他日期", occupancy.getMessage());
    }

    @Test
    @DisplayName("login_密码错误与用户不存在_返回同一个1005")
    void login_wrongPasswordOrUnknownPhone_sameCode() {
        LoginVO vo = authService.register(new RegisterDTO("13900001003", PASSWORD, "测试丙", COMMUNITY_ID));
        createdUserIds.add(vo.user().id());

        BizException wrongPassword = assertThrows(BizException.class,
                () -> authService.login(new LoginDTO("13900001003", "654321")));
        BizException unknownPhone = assertThrows(BizException.class,
                () -> authService.login(new LoginDTO("13900009999", PASSWORD)));

        assertEquals(1005, wrongPassword.getCode());
        assertEquals(1005, unknownPhone.getCode());
        assertEquals(wrongPassword.getMessage(), unknownPhone.getMessage(),
                "两种失败必须同码同文案，否则等于提供手机号枚举接口");
    }

    @Test
    @DisplayName("login_账号被禁用_登录与刷新都返回1006")
    void login_disabledAccount_returnsAccountDisabled() {
        LoginVO vo = authService.register(new RegisterDTO("13900001004", PASSWORD, "测试丁", COMMUNITY_ID));
        createdUserIds.add(vo.user().id());

        User patch = new User();
        patch.setId(vo.user().id());
        patch.setStatus("DISABLED");
        userMapper.updateById(patch);

        BizException onLogin = assertThrows(BizException.class,
                () -> authService.login(new LoginDTO("13900001004", PASSWORD)));
        assertEquals(1006, onLogin.getCode());

        BizException onRefresh = assertThrows(BizException.class,
                () -> authService.refresh(new RefreshDTO(vo.refreshToken())));
        assertEquals(1006, onRefresh.getCode(), "禁用账号不能靠旧 refresh 令牌续命");
    }

    @Test
    @DisplayName("refresh_有效令牌_轮换jti且旧refresh失效")
    void refresh_validToken_rotatesJtiAndRejectsOldOne() {
        LoginVO first = authService.register(new RegisterDTO("13900001005", PASSWORD, "测试戊", COMMUNITY_ID));
        createdUserIds.add(first.user().id());

        LoginVO second = authService.refresh(new RefreshDTO(first.refreshToken()));
        assertNotEquals(first.refreshToken(), second.refreshToken(), "每次刷新必须换新令牌");

        String newJti = jwtUtil.parse(second.refreshToken()).getId();
        assertTrue(refreshTokenStore.matches(first.user().id(), newJti));

        BizException reuseOld = assertThrows(BizException.class,
                () -> authService.refresh(new RefreshDTO(first.refreshToken())));
        assertEquals(1003, reuseOld.getCode(), "被轮换掉的旧 refresh 必须立刻失效");
    }

    @Test
    @DisplayName("refresh_拿access令牌调用_返回1003")
    void refresh_withAccessToken_returnsTokenInvalid() {
        LoginVO vo = authService.register(new RegisterDTO("13900001006", PASSWORD, "测试己", COMMUNITY_ID));
        createdUserIds.add(vo.user().id());

        BizException e = assertThrows(BizException.class,
                () -> authService.refresh(new RefreshDTO(vo.accessToken())));
        assertEquals(1003, e.getCode());
    }

    @Test
    @DisplayName("logout_删除白名单_旧refresh无法再换令牌")
    void logout_revokesRefreshToken() {
        LoginVO vo = authService.register(new RegisterDTO("13900001007", PASSWORD, "测试庚", COMMUNITY_ID));
        createdUserIds.add(vo.user().id());

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new LoginUser(vo.user().id(), "测试庚", "USER"),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        assertEquals(vo.user().id(), userService.me().id(), "me() 必须取当前登录用户");

        authService.logout();

        BizException e = assertThrows(BizException.class,
                () -> authService.refresh(new RefreshDTO(vo.refreshToken())));
        assertEquals(1003, e.getCode());
    }

    @Test
    @DisplayName("resetPassword_要素齐全_新密码可登录且旧refresh被作废")
    void resetPassword_matched_setsNewPasswordAndRevokesSessions() {
        LoginVO vo = authService.register(new RegisterDTO("13900001008", PASSWORD, "测试辛", COMMUNITY_ID));
        Long userId = vo.user().id();
        createdUserIds.add(userId);
        assertTrue(refreshTokenStore.matches(userId, jwtUtil.parse(vo.refreshToken()).getId()),
                "前置：注册后白名单里应有该用户的 jti");

        authService.resetPassword(new ResetPasswordDTO("13900001008", "测试辛", COMMUNITY_ID, "newPass2026"));

        // 旧密码失效、新密码可登录
        BizException old = assertThrows(BizException.class,
                () -> authService.login(new LoginDTO("13900001008", PASSWORD)));
        assertEquals(1005, old.getCode(), "旧密码必须失效");
        LoginVO relogin = authService.login(new LoginDTO("13900001008", "newPass2026"));
        assertEquals(userId, relogin.user().id());

        // 改密意味着原主人可能已失去控制权：旧的 refresh 白名单必须一起清掉
        BizException reuse = assertThrows(BizException.class,
                () -> authService.refresh(new RefreshDTO(vo.refreshToken())));
        assertEquals(1003, reuse.getCode(), "改密后旧 refresh 必须失效");
    }

    @Test
    @DisplayName("resetPassword_昵称或社区不匹配_统一返回2001同一句文案")
    void resetPassword_mismatchedElements_sameParamError() {
        LoginVO vo = authService.register(new RegisterDTO("13900001009", PASSWORD, "测试壬", COMMUNITY_ID));
        createdUserIds.add(vo.user().id());

        BizException wrongNickname = assertThrows(BizException.class,
                () -> authService.resetPassword(new ResetPasswordDTO("13900001009", "别人昵称", COMMUNITY_ID, "newPass2026")));
        assertEquals(2001, wrongNickname.getCode());

        // 该账号注册时选过社区，故社区也必须匹配
        BizException wrongCommunity = assertThrows(BizException.class,
                () -> authService.resetPassword(new ResetPasswordDTO("13900001009", "测试壬", 2L, "newPass2026")));
        assertEquals(2001, wrongCommunity.getCode());

        BizException unknownPhone = assertThrows(BizException.class,
                () -> authService.resetPassword(new ResetPasswordDTO("13900009999", "测试壬", COMMUNITY_ID, "newPass2026")));
        assertEquals(2001, unknownPhone.getCode());

        assertEquals(wrongNickname.getMessage(), unknownPhone.getMessage(),
                "三种不匹配必须同一句话，否则这个接口就成了手机号/昵称查询器");
    }

    @Test
    @DisplayName("resetPassword_没设置社区的账号_只校验手机号与昵称")
    void resetPassword_withoutCommunity_skipsCommunityCheck() {
        LoginVO vo = authService.register(new RegisterDTO("13900001010", PASSWORD, "测试癸", null));
        createdUserIds.add(vo.user().id());

        authService.resetPassword(new ResetPasswordDTO("13900001010", "测试癸", null, "newPass2026"));
        LoginVO relogin = authService.login(new LoginDTO("13900001010", "newPass2026"));
        assertEquals(vo.user().id(), relogin.user().id());
    }
}