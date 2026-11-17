package com.duyikang.rental.module.user.service.impl;

import com.duyikang.rental.common.constant.ErrorCode;
import com.duyikang.rental.common.exception.BizException;
import com.duyikang.rental.module.item.mapper.CommunityMapper;
import com.duyikang.rental.module.user.convert.UserConvert;
import com.duyikang.rental.module.user.dto.UpdateProfileDTO;
import com.duyikang.rental.module.user.entity.User;
import com.duyikang.rental.module.user.mapper.UserMapper;
import com.duyikang.rental.module.user.service.UserService;
import com.duyikang.rental.module.user.vo.UserVO;
import com.duyikang.rental.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 用户资料服务实现。
 *
 * <p>只改「传了值的字段」：前端做局部编辑时不必回传全量，
 * 也避免把未传字段覆盖成 null（MyBatis-Plus 的 {@code updateById} 本身
 * 只更新非 null 字段，这里再显式判空是为了语义清晰）。</p>
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final CommunityMapper communityMapper;

    @Override
    public UserVO me() {
        return UserConvert.toVO(loadCurrentUser());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO updateProfile(UpdateProfileDTO dto) {
        User current = loadCurrentUser();
        if (dto.communityId() != null && communityMapper.selectById(dto.communityId()) == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "所选社区不存在");
        }

        User patch = new User();
        patch.setId(current.getId());
        if (StringUtils.hasText(dto.nickname())) {
            patch.setNickname(dto.nickname());
        }
        if (StringUtils.hasText(dto.avatarUrl())) {
            patch.setAvatarUrl(dto.avatarUrl());
        }
        if (dto.communityId() != null) {
            patch.setCommunityId(dto.communityId());
        }
        userMapper.updateById(patch);
        return UserConvert.toVO(userMapper.selectById(current.getId()));
    }

    /**
     * 取当前登录用户实体。
     *
     * <p>不信任任何请求参数里的 userId：只认 {@code SecurityContextHolder} 里的令牌主体，
     * 否则任何登录用户都能通过改参数看别人的资料（PROJECT_RULES.md §7.3）。</p>
     */
    private User loadCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser loginUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        User user = userMapper.selectById(loginUser.userId());
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return user;
    }
}
