package com.duyikang.rental.module.admin.service;

import com.duyikang.rental.module.admin.vo.StatsOverviewVO;
import com.duyikang.rental.module.admin.vo.StatsTrendVO;

/**
 * 管理端看板统计（只读，不改任何数据）。
 */
public interface StatsService {

    /** 概览卡片与状态分布。 */
    StatsOverviewVO overview();

    /**
     * 趋势折线。
     *
     * @param days 天数，`1–30`，越界抛 2001
     */
    StatsTrendVO trend(Integer days);
}
