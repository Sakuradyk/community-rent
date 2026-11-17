package com.duyikang.rental.module.user.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

/**
 * 用户视图对象（返回给前端的用户信息）。
 *
 * <p><b>绝不直接返回 {@code User} 实体</b>：实体里有 {@code passwordHash}
 * 与逻辑删除列，返回它就是泄露凭证（PROJECT_RULES.md §4.3、§7.3）。</p>
 *
 * @param id          用户主键，序列化为字符串（JS 的 Number 只有 53 位精度，Long 会丢位）
 * @param nickname    昵称
 * @param phone       手机号，<b>已脱敏</b>（138****0001）
 * @param avatarUrl   头像相对路径
 * @param communityId 所属社区主键，可为 null
 * @param creditScore 当前信誉分
 * @param status      账号状态 NORMAL / DISABLED
 * @param createdAt   注册时间
 */
public record UserVO(
        @JsonSerialize(using = ToStringSerializer.class)
        Long id,
        String nickname,
        String phone,
        String avatarUrl,
        @JsonSerialize(using = ToStringSerializer.class)
        Long communityId,
        Integer creditScore,
        String status,
        LocalDateTime createdAt
) {
}
