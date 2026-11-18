package com.duyikang.rental.module.item.service;

import com.duyikang.rental.common.result.PageResult;
import com.duyikang.rental.module.item.dto.ItemAuditDTO;
import com.duyikang.rental.module.item.vo.AdminItemVO;

/**
 * 物品审核（管理端）。
 *
 * <p>契约见 docs/接口文档.md §2.9 / §2.10；权限由 SecurityConfig 的 {@code /api/admin/**} 统一拦，
 * 这里不再重复判断角色（§7.3：权限判断只做一次，避免两处口径不一致）。</p>
 */
public interface ItemAdminService {

    /**
     * 审核列表。
     *
     * @param status   物品状态，为空时默认只看待审核（默认值来自业务口径，不是页面偏好）
     * @param keyword  标题模糊匹配，可空
     * @param pageNum  页码，从 1 起
     * @param pageSize 每页条数，上限 100
     */
    PageResult<AdminItemVO> page(String status, String keyword, Integer pageNum, Integer pageSize);

    /**
     * 审核。
     *
     * <p>只允许对待审核物品操作：通过 → 在架，驳回 → 审核驳回（并记录驳回理由）。</p>
     */
    void audit(Long itemId, ItemAuditDTO dto);
}
