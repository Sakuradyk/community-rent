package com.duyikang.rental.common.exception;

import com.duyikang.rental.common.constant.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 业务异常单元测试：重点验证「错误码 + 消息 + 附加明细」三者不会在传递中丢失。
 *
 * <p>为什么要测 data：K2 时段冲突必须把 conflictDates / suggestRanges 带回前端，
 * 否则前端只能提示「已被占用」，日历无法高亮（PROJECT_RULES.md §8.3）。</p>
 */
class BizExceptionTest {

    @Test
    @DisplayName("occupancyConflict_带明细_码为4090且明细保留")
    void occupancyConflict_withDetail_keepsCodeAndData() {
        Map<String, Object> data = Map.of("conflictDates", List.of("2026-10-02"));

        BizException e = BizException.occupancyConflict("2026-10-02 已被租出", data);

        assertEquals(4090, e.getCode());
        assertEquals("2026-10-02 已被租出", e.getMessage());
        assertSame(data, e.getData());
    }

    @Test
    @DisplayName("occupancyConflict_不带明细_码为4090且data为null")
    void occupancyConflict_withoutDetail_hasNullData() {
        BizException e = BizException.occupancyConflict();

        assertEquals(ErrorCode.OCCUPANCY_CONFLICT.getCode(), e.getCode());
        assertEquals(ErrorCode.OCCUPANCY_CONFLICT.getMessage(), e.getMessage());
        assertNull(e.getData());
    }

    @Test
    @DisplayName("paramError_默认消息_码为2001")
    void paramError_returnsParamCode() {
        BizException e = BizException.paramError("开始日期不能早于今天");

        assertEquals(2001, e.getCode());
        assertEquals("开始日期不能早于今天", e.getMessage());
        assertNull(e.getData());
    }

    @Test
    @DisplayName("of_任意码加数据_两字段都对")
    void of_withErrorCodeAndData_keepsBoth() {
        BizException e = BizException.of(ErrorCode.CREDIT_NOT_ENOUGH, "required=70");

        assertEquals(3002, e.getCode());
        assertEquals(ErrorCode.CREDIT_NOT_ENOUGH.getMessage(), e.getMessage());
        assertEquals("required=70", e.getData());
    }

    @Test
    @DisplayName("构造_int码加消息加数据_不走枚举")
    void constructor_withIntCode_keepsRawCode() {
        BizException e = new BizException(5003, "抢锁失败", "lock:item:1");

        assertEquals(5003, e.getCode());
        assertEquals("抢锁失败", e.getMessage());
        assertEquals("lock:item:1", e.getData());
    }
}
