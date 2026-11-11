package com.duyikang.rental.module.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.duyikang.rental.module.user.entity.UserRole;
import org.apache.ibatis.annotations.Mapper;

/**
 * UserRole 表 Mapper（对应 user_role）。
 * 默认继承 BaseMapper 的 CRUD；需要复杂 SQL 时再加方法 + resources/mapper 下的 XML。
 */
@Mapper
public interface UserRoleMapper extends BaseMapper<UserRole> {
}