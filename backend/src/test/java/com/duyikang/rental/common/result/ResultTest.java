package com.duyikang.rental.common.result;

import com.duyikang.rental.common.constant.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 统一响应体单元测试。
 */
class ResultTest {

    @Test
    @DisplayName("ok_普通数据_返回code为0且携带数据")
    void ok_withData_returnsSuccessCode() {
        Result<String> result = Result.ok("hello");

        assertEquals(0, result.getCode());
        assertEquals("成功", result.getMessage());
        assertEquals("hello", result.getData());
        assertTrue(result.isSuccess());
    }

    @Test
    @DisplayName("ok_无参_返回成功且data为null")
    void ok_withoutData_returnsNullData() {
        Result<Void> result = Result.ok();

        assertEquals(0, result.getCode());
        assertNull(result.getData());
        assertTrue(result.isSuccess());
    }

    @Test
    @DisplayName("fail_错误码枚举_取枚举默认消息")
    void fail_withErrorCode_usesDefaultMessage() {
        Result<Void> result = Result.fail(ErrorCode.OCCUPANCY_CONFLICT);

        assertEquals(4090, result.getCode());
        assertEquals("该时段已被占用，请选择其他日期", result.getMessage());
        assertNull(result.getData());
    }

    @Test
    @DisplayName("fail_自定义消息_覆盖枚举默认消息")
    void fail_withCustomMessage_overridesMessage() {
        Result<Void> result = Result.fail(ErrorCode.PARAM_ERROR, "开始日期不能早于今天");

        assertEquals(2001, result.getCode());
        assertEquals("开始日期不能早于今天", result.getMessage());
    }

    @Test
    @DisplayName("isSuccess_非0业务码_返回false")
    void isSuccess_whenBizError_returnsFalse() {
        assertTrue(!Result.fail(ErrorCode.CREDIT_NOT_ENOUGH).isSuccess());
    }
}
