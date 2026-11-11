package com.duyikang.rental.module.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.duyikang.rental.module.user.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * User 表 Mapper（对应 user）。
 * 默认继承 BaseMapper 的 CRUD；需要复杂 SQL 时再加方法 + resources/mapper 下的 XML。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}