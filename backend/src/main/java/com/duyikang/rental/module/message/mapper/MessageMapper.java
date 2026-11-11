package com.duyikang.rental.module.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.duyikang.rental.module.message.entity.Message;
import org.apache.ibatis.annotations.Mapper;

/**
 * Message 表 Mapper（对应 message）。
 * 默认继承 BaseMapper 的 CRUD；需要复杂 SQL 时再加方法 + resources/mapper 下的 XML。
 */
@Mapper
public interface MessageMapper extends BaseMapper<Message> {
}