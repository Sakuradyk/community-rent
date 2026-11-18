package com.duyikang.rental.module.item.controller;

import com.duyikang.rental.common.result.PageResult;
import com.duyikang.rental.common.result.Result;
import com.duyikang.rental.module.item.dto.ItemQuery;
import com.duyikang.rental.module.item.dto.ItemSaveDTO;
import com.duyikang.rental.module.item.dto.ItemShelfDTO;
import com.duyikang.rental.module.item.service.ItemService;
import com.duyikang.rental.module.item.vo.CalendarVO;
import com.duyikang.rental.module.item.vo.ItemCardVO;
import com.duyikang.rental.module.item.vo.ItemDetailVO;
import com.duyikang.rental.module.item.vo.ItemIdVO;
import com.duyikang.rental.module.item.vo.MyItemVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 物品接口（用户侧）。
 *
 * <p>契约：docs/接口文档.md §2.1–§2.8。分层职责（PROJECT_RULES.md §4.3）：
 * Controller 只做参数绑定 + 调 Service + 包 Result，不写业务判断。</p>
 *
 * <p>注意 GET 列表 / 详情 / 日历是匿名的（游客可浏览，登录后才有"就近优先"排序），
 * 白名单在 SecurityConfig 里按 HttpMethod.GET 单独放行。</p>
 */
@RestController
@RequestMapping("/api/items")
@RequiredArgsConstructor
public class ItemController {

    private final ItemService itemService;

    /** 发布物品：落待审核，返回新 id（`{"id":"12"}`，id 按契约是字符串）。 */
    @PostMapping
    public Result<ItemIdVO> create(@Valid @RequestBody ItemSaveDTO dto) {
        return Result.ok(new ItemIdVO(itemService.create(dto)));
    }

    /** 编辑物品：仅归属人，且只有待审核/已下架/审核驳回可改，改完回到待审核。 */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ItemSaveDTO dto) {
        itemService.update(id, dto);
        return Result.ok();
    }

    /** 上下架：上架只能来自已下架，下架要求无进行中订单。 */
    @PutMapping("/{id}/shelf")
    public Result<Void> shelf(@PathVariable Long id, @Valid @RequestBody ItemShelfDTO dto) {
        itemService.shelf(id, dto);
        return Result.ok();
    }

    /** 物品检索：关键词 / 类目 / 社区 / 价格区间 / 可租日期 + 分页 + 同小区优先。 */
    @GetMapping
    public Result<PageResult<ItemCardVO>> list(@ModelAttribute ItemQuery query) {
        return Result.ok(itemService.list(query));
    }

    /** 我的物品：必须登录，放在 /mine 而不是 /api/items?owner=me（避免把自己塞进筛选参数）。 */
    @GetMapping("/mine")
    public Result<PageResult<MyItemVO>> mine(String status, Integer pageNum, Integer pageSize) {
        return Result.ok(itemService.mine(status, pageNum, pageSize));
    }

    /** 物品详情：待审核/驳回仅归属人与管理员可见，其他人按"不存在"处理。 */
    @GetMapping("/{id}")
    public Result<ItemDetailVO> detail(@PathVariable Long id) {
        return Result.ok(itemService.detail(id));
    }

    /** 占用日历：只读，与下单共用同一套占用口径。 */
    @GetMapping("/{id}/calendar")
    public Result<CalendarVO> calendar(@PathVariable Long id, String month) {
        return Result.ok(itemService.calendar(id, month));
    }

    /** 删除物品：逻辑删除，有进行中订单时拒绝。 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        itemService.delete(id);
        return Result.ok();
    }
}
