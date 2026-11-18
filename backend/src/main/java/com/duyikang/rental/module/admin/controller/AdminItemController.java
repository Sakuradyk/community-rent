package com.duyikang.rental.module.admin.controller;

import com.duyikang.rental.common.result.PageResult;
import com.duyikang.rental.common.result.Result;
import com.duyikang.rental.module.item.dto.ItemAuditDTO;
import com.duyikang.rental.module.item.service.ItemAdminService;
import com.duyikang.rental.module.item.vo.AdminItemVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端物品接口。
 *
 * <p>契约：docs/接口文档.md §2.9 / §2.10。整个 {@code /api/admin/**} 由 SecurityConfig 统一要求
 * {@code ROLE_ADMIN}，因此这里不再写角色判断（权限口径只留一处，PROJECT_RULES.md §7.3）。</p>
 */
@RestController
@RequestMapping("/api/admin/items")
@RequiredArgsConstructor
public class AdminItemController {

    private final ItemAdminService itemAdminService;

    /** 审核列表：status 为空时默认只看待审核，供管理端"待办"页直接使用。 */
    @GetMapping
    public Result<PageResult<AdminItemVO>> page(String status, String keyword, Integer pageNum, Integer pageSize) {
        return Result.ok(itemAdminService.page(status, keyword, pageNum, pageSize));
    }

    /** 审核：通过 → 在架；驳回 → 审核驳回且必须写理由。 */
    @PostMapping("/{id}/audit")
    public Result<Void> audit(@PathVariable Long id, @Valid @RequestBody ItemAuditDTO dto) {
        itemAdminService.audit(id, dto);
        return Result.ok();
    }
}
