package com.duyikang.rental.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户账号实体。只存账号信息，不存"出租方/承租方"身份——双边角色由订单的 ownerId / renterId 表达（K1）。
 */
@Data
@TableName("user")
public class User {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 手机号，登录名
     */
    private String phone;

    /**
     * BCrypt 哈希（$2a$10$ 开头，60 位）
     */
    private String passwordHash;

    /**
     * 头像地址
     */
    private String avatarUrl;

    /**
     * 所属社区，引用 community.id；NULL = 未填写，不参与同小区优先排序
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long communityId;

    /**
     * 信誉分，初始值取 credit_policy.init_score
     */
    private Integer creditScore;

    /**
     * NORMAL 正常 / DISABLED 禁用
     */
    private String status;

    /**
     * 最近登录时间
     */
    private LocalDateTime lastLoginAt;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 逻辑删除：0 正常 / 1 已删
     */
    @TableLogic
    private Integer deleted;

}
