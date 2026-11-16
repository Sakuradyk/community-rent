package com.duyikang.rental.module.item.service;

import com.duyikang.rental.module.item.vo.CommunityVO;

import java.util.List;

/**
 * 社区字典服务。
 */
public interface CommunityService {

    /**
     * 查询启用中的社区下拉选项，按 sort_no、id 升序。
     *
     * <p>停用（enabled = 0）的社区不返回，但历史物品/用户对它的引用仍然有效——
     * 停用只影响「新填写时还能不能选」，不影响已经发生的数据。</p>
     *
     * @return 下拉选项列表，可能为空（尚未配置任何社区）
     */
    List<CommunityVO> listEnabled();
}
