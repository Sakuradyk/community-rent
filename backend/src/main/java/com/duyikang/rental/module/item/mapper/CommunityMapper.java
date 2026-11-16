package com.duyikang.rental.module.item.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.duyikang.rental.module.item.entity.Community;
import org.apache.ibatis.annotations.Mapper;

/**
 * Community 表 Mapper（对应 community）。
 * 下拉查询走 BaseMapper.selectList + LambdaQueryWrapper（只查 enabled = 1，按 sort_no 排序），不需要 XML。
 */
@Mapper
public interface CommunityMapper extends BaseMapper<Community> {
}
