package com.duyikang.rental.module.admin;

import com.duyikang.rental.common.constant.ErrorCode;
import com.duyikang.rental.common.exception.BizException;
import com.duyikang.rental.module.admin.service.StatsService;
import com.duyikang.rental.module.admin.vo.StatsOverviewVO;
import com.duyikang.rental.module.admin.vo.StatsTrendVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 看板统计集成测试（切片 6）。
 *
 * <p>不锁死具体数值（订单/用户会随真实使用增长，`MapperIntegrationTest` 踩过这个坑）——
 * 只锁"内部一致"与"结构正确"：分状态之和等于总数、点数等于请求天数、末位是今天、越界报 2001。</p>
 */
@SpringBootTest
@Transactional
class StatsServiceTest {

    @Autowired
    private StatsService statsService;

    @Test
    @DisplayName("概览：分状态订单数之和等于 orderTotal，金额与平均分非负")
    void overview_statusSumsMatchTotal() {
        StatsOverviewVO overview = statsService.overview();

        long sumByStatus = overview.orderPending() + overview.orderConfirmed() + overview.orderInUse()
                + overview.orderOverdue() + overview.orderCompleted() + overview.orderCancelled();

        assertThat(overview.orderTotal()).isEqualTo(sumByStatus);
        assertThat(overview.userTotal()).isPositive();
        assertThat(overview.itemTotal()).isPositive();
        assertThat(overview.itemOnShelf()).isLessThanOrEqualTo(overview.itemTotal());
        assertThat(overview.gmvTotal()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(overview.gmvIn30Days()).isLessThanOrEqualTo(overview.gmvTotal());
        assertThat(overview.creditAvg()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(overview.updatedAt()).isNotNull();
    }

    @Test
    @DisplayName("趋势：7 天 = 7 个点、日期升序、末位是今天、金额非空")
    void trend_sevenPointsAscendingAndEndsToday() {
        StatsTrendVO trend = statsService.trend(7);

        List<StatsTrendVO.PointVO> points = trend.points();
        assertThat(trend.days()).isEqualTo(7);
        assertThat(points).hasSize(7);
        assertThat(points.get(6).date()).isEqualTo(LocalDate.now().toString());
        assertThat(points).isSortedAccordingTo((a, b) -> a.date().compareTo(b.date()));
        assertThat(points).allSatisfy(p -> {
            assertThat(p.gmv()).isNotNull();
            assertThat(p.orderCount()).isGreaterThanOrEqualTo(0L);
            assertThat(p.newUsers()).isGreaterThanOrEqualTo(0L);
        });
    }

    @Test
    @DisplayName("趋势默认 7 天：days 传 null 时按 7 处理")
    void trend_nullDefaultsToSeven() {
        assertThat(statsService.trend(null).points()).hasSize(7);
    }

    @Test
    @DisplayName("趋势越界（0 / 31）→ 2001")
    void trend_outOfRange_throwsParamError() {
        assertThatThrownBy(() -> statsService.trend(0))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode()));
        assertThatThrownBy(() -> statsService.trend(31))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode()));
    }
}
