package com.duyikang.rental.module.user.service;

import com.duyikang.rental.module.user.dto.UpdateProfileDTO;
import com.duyikang.rental.module.user.vo.UserVO;

/**
 * 用户资料服务。
 *
 * <p>只操作「当前登录用户」自己的资料：方法不接收 userId 参数，
 * 一律从 {@code SecurityContextHolder} 取，避免越权改他人资料
 * （PROJECT_RULES.md §7.3）。</p>
 */
public interface UserService {

    /** 当前登录用户信息。 */
    UserVO me();

    /**
     * 修改当前登录用户的资料（传什么改什么）。
     *
     * @throws com.duyikang.rental.common.exception.BizException 社区不存在(2001)
     */
    UserVO updateProfile(UpdateProfileDTO dto);
}
