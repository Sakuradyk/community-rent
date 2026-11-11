package com.duyikang.rental.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色字典实体。本课题只有 USER / ADMIN，Spring Security 里映射为 ROLE_USER / ROLE_ADMIN。
 */
@Data
@TableName("role")
public class Role {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 角色码：USER / ADMIN
     */
    private String code;

    /**
     * 角色名称
     */
    private String name;

    /**
     * 说明
     */
    private String description;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

}
