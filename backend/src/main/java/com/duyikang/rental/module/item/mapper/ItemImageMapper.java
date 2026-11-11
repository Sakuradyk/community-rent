package com.duyikang.rental.module.item.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.duyikang.rental.module.item.entity.ItemImage;
import org.apache.ibatis.annotations.Mapper;

/**
 * ItemImage 表 Mapper（对应 item_image）。
 * 默认继承 BaseMapper 的 CRUD；需要复杂 SQL 时再加方法 + resources/mapper 下的 XML。
 */
@Mapper
public interface ItemImageMapper extends BaseMapper<ItemImage> {
}