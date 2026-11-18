package com.duyikang.rental.module.item.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 物品列表/我的物品/待审列表的查询条件（GET 参数绑定，用可变 Bean 而非 record：
 * {@code @ModelAttribute} 对 setter 绑定的兼容性最好，缺省参数也能按字段默认值生效）。
 *
 * <p>{@code startDate} / {@code endDate} 必须成对出现才生效——只给一头无法判断区间，
 * 静默忽略比报错更难排查，所以 Service 里一头有一头无时返回 2001。</p>
 */
@Data
public class ItemQuery {

    /** 关键词：匹配标题或描述。 */
    private String keyword;

    /** 类目：传一级时自动含其二级子类目。 */
    private Long categoryId;

    /** 所在社区。 */
    private Long communityId;

    /** 日租金下限。 */
    private BigDecimal minPrice;

    /** 日租金上限。 */
    private BigDecimal maxPrice;

    /** 可租起始日（半开区间左端，含）。 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    /** 可租结束日（半开区间右端，不含）。 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    /** 页码，从 1 起。 */
    private Integer pageNum = 1;

    /** 每页条数，上限 100。 */
    private Integer pageSize = 10;
}
