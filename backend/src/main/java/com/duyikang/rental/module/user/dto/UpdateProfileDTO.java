package com.duyikang.rental.module.user.dto;

import jakarta.validation.constraints.Size;

/**
 * 修改个人资料入参（全部可选，传了才改）。
 *
 * <p>为什么不放手机号与密码：手机号是登录名，改了等于换账号，需专门的
 * 「换绑」流程（本课题不做，需求总结 §10）；密码修改涉及旧密码校验，
 * 与资料修改不是一个语义，暂不合并。</p>
 *
 * @param nickname    新昵称
 * @param avatarUrl   新头像地址（先调 `/api/files/upload` 拿到的相对路径）
 * @param communityId 新社区主键
 */
public record UpdateProfileDTO(
        @Size(min = 2, max = 16, message = "昵称长度需为 2-16 位")
        String nickname,

        @Size(max = 255, message = "头像地址过长")
        String avatarUrl,

        Long communityId
) {
}
