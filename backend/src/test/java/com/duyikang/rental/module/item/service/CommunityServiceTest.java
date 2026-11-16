package com.duyikang.rental.module.item.service;

import com.duyikang.rental.module.item.vo.CommunityVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 社区字典服务集成测试：连真实 rental 库，@Transactional 自动回滚（PROJECT_RULES.md §12）。
 */
@SpringBootTest
@Transactional
@DisplayName("社区字典：下拉只返回启用项")
class CommunityServiceTest {

    @Autowired
    private CommunityService communityService;

    @Test
    @DisplayName("listEnabled_排除停用小区_按sort_no升序")
    void listEnabled_excludesDisabled_orderedBySortNo() {
        List<CommunityVO> list = communityService.listEnabled();

        // 种子 6 个社区，其中紫竹园 enabled = 0 → 下拉只有 5 个
        assertThat(list).hasSize(5);
        assertThat(list).extracting(CommunityVO::name).doesNotContain("紫竹园");
        // sort_no 升序：阳光花园(10) 排第一
        assertThat(list.get(0).name()).isEqualTo("阳光花园");
        // 前三个同在城东街道，第二档「同片区」排序才有东西可用
        assertThat(list.stream().limit(3).map(CommunityVO::district)).containsOnly("城东街道");
        // id 必须带出来（前端提交 communityId 用），且非空
        assertThat(list).allSatisfy(vo -> assertThat(vo.id()).isNotNull());
    }
}
