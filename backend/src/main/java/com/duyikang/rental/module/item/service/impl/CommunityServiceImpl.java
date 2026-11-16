package com.duyikang.rental.module.item.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.duyikang.rental.module.item.entity.Community;
import com.duyikang.rental.module.item.mapper.CommunityMapper;
import com.duyikang.rental.module.item.service.CommunityService;
import com.duyikang.rental.module.item.vo.CommunityVO;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 社区字典服务实现。
 */
@Service
public class CommunityServiceImpl implements CommunityService {

    /**
     * 启用状态常量：1 可选
     */
    private static final int ENABLED = 1;

    private final CommunityMapper communityMapper;

    public CommunityServiceImpl(CommunityMapper communityMapper) {
        this.communityMapper = communityMapper;
    }

    @Override
    public List<CommunityVO> listEnabled() {
        List<Community> communities = communityMapper.selectList(
                Wrappers.<Community>lambdaQuery()
                        .eq(Community::getEnabled, ENABLED)
                        .orderByAsc(Community::getSortNo)
                        .orderByAsc(Community::getId)
        );
        return communities.stream()
                .map(c -> new CommunityVO(c.getId(), c.getName(), c.getDistrict()))
                .toList();
    }
}
