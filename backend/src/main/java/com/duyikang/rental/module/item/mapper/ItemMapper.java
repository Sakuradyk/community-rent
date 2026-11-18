package com.duyikang.rental.module.item.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.duyikang.rental.module.item.dto.ItemQuery;
import com.duyikang.rental.module.item.entity.Item;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * Item 表 Mapper。默认 CRUD 走 BaseMapper；三个需要"跨表 + 表达式排序"的查询写在
 * {@code resources/mapper/ItemMapper.xml}。
 */
@Mapper
public interface ItemMapper extends BaseMapper<Item> {

    /**
     * 在架物品分页（列表页）。
     *
     * <p>排序是本项目唯一在 SQL 里写 CASE 表达式的地方：同小区 → 同片区 → 热度 → 新发布。
     * 之所以不用 QueryWrapper 拼：{@code ORDER BY} 需要表达式，而 Wrapper 只接受列名，
     * 用 {@code last()} 拼字符串会把 {@code district} 这种库里的文本拼进 SQL，属注入面。</p>
     *
     * @param page            分页对象（分页插件会改写 SQL）
     * @param query           前端筛选条件
     * @param categoryIds     已展开的类目 id 集合（传一级类目时含其二级）；null 表示不筛类目
     * @param myCommunityId   当前登录用户的社区；未登录传 null（跳过第一档）
     * @param myDistrict      当前登录用户社区所属片区；无社区传 null（跳过第二档）
     * @param priorityEnabled {@code sys_config['community.priority.enabled']} 是否开启就近优先
     * @param excludedIds     与该日期区间冲突、必须排除的物品 id（未按日期筛选时传 null）
     */
    Page<Item> selectForSale(Page<Item> page,
                             @Param("q") ItemQuery query,
                             @Param("categoryIds") List<Long> categoryIds,
                             @Param("myCommunityId") Long myCommunityId,
                             @Param("myDistrict") String myDistrict,
                             @Param("priorityEnabled") boolean priorityEnabled,
                             @Param("excludedIds") List<Long> excludedIds);

    /**
     * 查在给定半开区间内已被占用的物品 id 集合（列表页"可租日期"筛选用）。
     *
     * <p>占用口径与日历、下单完全一致：只看 {@code CONFIRMED / IN_USE / OVERDUE} 三态的订单，
     * {@code PENDING} 不占时段（需求总结 §6）。</p>
     */
    List<Long> selectOccupiedItemIds(@Param("from") LocalDate from,
                                     @Param("to") LocalDate to);

    /**
     * 某物品在某区间内的已占用日期（日历接口用，升序去重）。
     */
    List<LocalDate> selectOccupiedDates(@Param("itemId") Long itemId,
                                        @Param("from") LocalDate from,
                                        @Param("to") LocalDate to);
}
