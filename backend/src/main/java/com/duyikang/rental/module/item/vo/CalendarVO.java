package com.duyikang.rental.module.item.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.util.List;

/**
 * 某物品在某月的占用日历（FR-M1-05）。
 *
 * <p>{@code availableDates} 是**补齐**的整月可租日：前端直接按这两个数组标色，
 * 不做任何日期算术（F1：前端不参与业务判定）。切片 3 的"可租日期筛选"必须复用同一套
 * 占用查询，不得另写区间逻辑——否则日历显示可租、下单却 4090，是最难解释的 bug。</p>
 */
public record CalendarVO(

        @JsonSerialize(using = ToStringSerializer.class)
        Long itemId,
        String month,
        List<String> occupiedDates,
        List<String> availableDates
) {
}
