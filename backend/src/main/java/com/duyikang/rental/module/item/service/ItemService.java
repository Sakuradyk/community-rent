package com.duyikang.rental.module.item.service;

import com.duyikang.rental.common.result.PageResult;
import com.duyikang.rental.module.item.dto.ItemQuery;
import com.duyikang.rental.module.item.dto.ItemSaveDTO;
import com.duyikang.rental.module.item.dto.ItemShelfDTO;
import com.duyikang.rental.module.item.vo.CalendarVO;
import com.duyikang.rental.module.item.vo.ItemCardVO;
import com.duyikang.rental.module.item.vo.ItemDetailVO;
import com.duyikang.rental.module.item.vo.MyItemVO;

/**
 * 物品模块（用户侧）业务接口。契约见 docs/接口文档.md §2。
 */
public interface ItemService {

    /** 发布：落 {@code PENDING} 待审，封面取第一张图。返回新物品 id。 */
    Long create(ItemSaveDTO dto);

    /** 编辑：整体替换，状态回到 {@code PENDING} 并清空审核痕迹。 */
    void update(Long itemId, ItemSaveDTO dto);

    /** 上下架：{@code OFF_SHELF <-> ON_SHELF}，有进行中订单不许下架。 */
    void shelf(Long itemId, ItemShelfDTO dto);

    /** 在架物品列表（匿名可访问；登录时按"同小区 > 同片区"优先）。 */
    PageResult<ItemCardVO> list(ItemQuery query);

    /** 详情（匿名可访问；{@code PENDING/REJECTED} 仅归属人与管理员可见）。 */
    ItemDetailVO detail(Long itemId);

    /** 占用日历（匿名可访问，FR-M1-05）。 */
    CalendarVO calendar(Long itemId, String month);

    /** 我的物品（登录，含全部状态）。 */
    PageResult<MyItemVO> mine(String status, Integer pageNum, Integer pageSize);

    /** 删除（逻辑删除；有进行中订单不许删）。 */
    void delete(Long itemId);
}
