package com.duyikang.rental.module.item.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/**
 * 详情页的归属人摘要。
 *
 * <p><b>不含手机号</b>：详情页是匿名可访问的，手机号一律不出现在公开接口里
 * （PROJECT_RULES.md §10，另见 UserConvert.maskPhone 的用途只限于管理端）。</p>
 */
public record ItemOwnerVO(

        @JsonSerialize(using = ToStringSerializer.class)
        Long id,
        String nickname,
        String avatarUrl,
        Integer creditScore,
        String communityName
) {
}
