package com.duyikang.rental.module.user.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.duyikang.rental.common.constant.ErrorCode;
import com.duyikang.rental.common.exception.BizException;
import com.duyikang.rental.module.credit.entity.CreditPolicy;
import com.duyikang.rental.module.credit.mapper.CreditPolicyMapper;
import com.duyikang.rental.module.item.mapper.CommunityMapper;
import com.duyikang.rental.module.user.convert.UserConvert;
import com.duyikang.rental.module.user.dto.LoginDTO;
import com.duyikang.rental.module.user.dto.RefreshDTO;
import com.duyikang.rental.module.user.dto.RegisterDTO;
import com.duyikang.rental.module.user.entity.Role;
import com.duyikang.rental.module.user.entity.User;
import com.duyikang.rental.module.user.entity.UserRole;
import com.duyikang.rental.module.user.mapper.RoleMapper;
import com.duyikang.rental.module.user.mapper.UserMapper;
import com.duyikang.rental.module.user.mapper.UserRoleMapper;
import com.duyikang.rental.module.user.service.AuthService;
import com.duyikang.rental.module.user.vo.LoginVO;
import com.duyikang.rental.security.JwtUtil;
import com.duyikang.rental.security.LoginUser;
import com.duyikang.rental.security.RefreshTokenStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 认证服务实现。
 *
 * <p>三条要点（PROJECT_RULES.md §13）：</p>
 * <ol>
 *   <li>初始信誉分<b>读库</b>（{@code credit_policy.init_score}），不在代码里写 80；</li>
 *   <li>密码错与用户不存在返回<b>同一个</b> 1005，避免手机号被枚举；</li>
 *   <li>注册时 {@code user} 与 {@code user_role} 必须同事务，否则会出现"能登录但没有角色"的脏数据。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String ROLE_USER = "USER";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String STATUS_NORMAL = "NORMAL";
    /** 信誉策略是全局单行配置表，主键固定为 1。 */
    private static final long DEFAULT_POLICY_ID = 1L;

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final CommunityMapper communityMapper;
    private final CreditPolicyMapper creditPolicyMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RefreshTokenStore refreshTokenStore;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginVO register(RegisterDTO dto) {
        Long exists = userMapper.selectCount(
                Wrappers.<User>lambdaQuery().eq(User::getPhone, dto.phone()));
        if (exists != null && exists > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "手机号已注册");
        }
        if (dto.communityId() != null && communityMapper.selectById(dto.communityId()) == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "所选社区不存在");
        }

        User user = new User();
        user.setPhone(dto.phone());
        user.setNickname(dto.nickname());
        user.setPasswordHash(passwordEncoder.encode(dto.password()));
        user.setCommunityId(dto.communityId());
        user.setCreditScore(loadInitScore());
        user.setStatus(STATUS_NORMAL);
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.insert(user);

        Role userRole = findRoleByCode(ROLE_USER);
        UserRole link = new UserRole();
        link.setUserId(user.getId());
        link.setRoleId(userRole.getId());
        userRoleMapper.insert(link);

        log.info("用户注册成功 userId={} nickname={}", user.getId(), user.getNickname());
        return issueTokens(user, ROLE_USER);
    }

    @Override
    public LoginVO login(LoginDTO dto) {
        User user = userMapper.selectOne(
                Wrappers.<User>lambdaQuery().eq(User::getPhone, dto.phone()));
        // 故意合并两种失败：区分"用户不存在"与"密码错误"等于免费提供手机号枚举接口
        if (user == null || !passwordEncoder.matches(dto.password(), user.getPasswordHash())) {
            throw new BizException(ErrorCode.ACCOUNT_OR_PASSWORD_ERROR);
        }
        if (!STATUS_NORMAL.equals(user.getStatus())) {
            throw new BizException(ErrorCode.ACCOUNT_DISABLED);
        }

        User touch = new User();
        touch.setId(user.getId());
        touch.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(touch);

        String role = resolveRoleCode(user.getId());
        log.info("用户登录成功 userId={} role={}", user.getId(), role);
        return issueTokens(user, role);
    }

    @Override
    public LoginVO refresh(RefreshDTO dto) {
        JwtUtil.RefreshClaims claims = jwtUtil.resolveRefresh(dto.refreshToken());
        if (!claims.isOk()) {
            throw new BizException(claims.error());
        }
        User user = userMapper.selectById(claims.userId());
        if (user == null) {
            // 用户已被逻辑删除：令牌从"格式合法"退化为"主体不存在"
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }
        if (!STATUS_NORMAL.equals(user.getStatus())) {
            throw new BizException(ErrorCode.ACCOUNT_DISABLED);
        }
        // 白名单校验：登出 / 已被轮换掉的旧令牌在这里被拦下
        if (!refreshTokenStore.matches(user.getId(), claims.jti())) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }
        return issueTokens(user, resolveRoleCode(user.getId()));
    }

    @Override
    public void logout() {
        Long userId = currentUserId();
        refreshTokenStore.remove(userId);
        log.info("用户登出 userId={}", userId);
    }

    /**
     * 发一对令牌：refresh 先落白名单再返回，保证客户端拿到的令牌一定可用；
     * 轮换即覆盖旧 jti，旧 refresh 立刻失效。
     */
    private LoginVO issueTokens(User user, String role) {
        String jti = UUID.randomUUID().toString().replace("-", "");
        String refreshToken = jwtUtil.generateRefresh(user.getId(), jti);
        refreshTokenStore.save(user.getId(), jti);
        String accessToken = jwtUtil.generateAccess(user.getId(), user.getNickname(), role);
        return new LoginVO(accessToken, refreshToken, jwtUtil.accessExpiresInSeconds(),
                UserConvert.toVO(user));
    }

    /** 初始信誉分来自配置表；策略表缺失属部署错误，必须显式报错而不是默认 80。 */
    private Integer loadInitScore() {
        CreditPolicy policy = creditPolicyMapper.selectById(DEFAULT_POLICY_ID);
        if (policy == null || policy.getInitScore() == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "信誉策略未初始化（credit_policy 缺少 id=1）");
        }
        return policy.getInitScore();
    }

    private Role findRoleByCode(String code) {
        Role role = roleMapper.selectOne(Wrappers.<Role>lambdaQuery().eq(Role::getCode, code));
        if (role == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "角色未初始化：" + code);
        }
        return role;
    }

    /**
     * 解析用户角色码：多角色时 ADMIN 优先。
     *
     * <p>为什么只取一个：{@link LoginUser} 只承载单个 role，Spring Security 的
     * {@code hasRole} 也只认单一权限位；管理员天然拥有全部普通能力，故取高不取并。</p>
     */
    private String resolveRoleCode(Long userId) {
        List<UserRole> links = userRoleMapper.selectList(
                Wrappers.<UserRole>lambdaQuery().eq(UserRole::getUserId, userId));
        if (links.isEmpty()) {
            return ROLE_USER;
        }
        Set<Long> roleIds = links.stream().map(UserRole::getRoleId).collect(Collectors.toSet());
        List<Role> roles = roleMapper.selectBatchIds(roleIds);
        boolean isAdmin = roles.stream().anyMatch(r -> ROLE_ADMIN.equals(r.getCode()));
        return isAdmin ? ROLE_ADMIN : ROLE_USER;
    }

    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser loginUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return loginUser.userId();
    }
}
