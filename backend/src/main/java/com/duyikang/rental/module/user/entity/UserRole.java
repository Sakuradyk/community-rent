package com.duyikang.rental.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户-角色关联实体（RBAC）。uk_user_role(user_id, role_id) 保证同一用户不重复授予同一角色。
 */
@Data
@TableName("user_role")
public class UserRole {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 用户主键
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    /**
     * 角色主键
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long roleId;

    /**
     * 授予时间
     */
    private LocalDateTime createdAt;

}
