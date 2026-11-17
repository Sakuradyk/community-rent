package com.duyikang.rental.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 守住 docs/接口文档.md §0 的时间契约：
 * 时刻必须是 {@code yyyy-MM-dd HH:mm:ss}，日期必须是 {@code yyyy-MM-dd}，
 * 不能出现 {@code 2026-11-16T20:36:43} 这种 ISO 串（前端 dayjs 虽能解析，但契约不一致会导致联调踩坑）。
 */
@SpringBootTest
class JacksonConfigTest {

    @Autowired
    private ObjectMapper objectMapper;

    /** 探针：只用来验证序列化格式，不参与业务 */
    record TimeProbe(LocalDateTime at, LocalDate on) {
    }

    @Test
    @DisplayName("serialize_localDateTime_followsContractPattern")
    void serialize_localDateTime_followsContractPattern() throws Exception {
        TimeProbe probe = new TimeProbe(
                LocalDateTime.of(2026, 11, 17, 19, 5, 12),
                LocalDate.of(2026, 11, 20)
        );

        String json = objectMapper.writeValueAsString(probe);

        assertThat(json).contains("\"at\":\"2026-11-17 19:05:12\"");
        assertThat(json).contains("\"on\":\"2026-11-20\"");
        assertThat(json).doesNotContain("2026-11-17T19:05:12");
    }

    @Test
    @DisplayName("deserialize_contractPatterns_parsedToJavaTime")
    void deserialize_contractPatterns_parsedToJavaTime() throws Exception {
        String json = "{\"at\":\"2026-11-17 19:05:12\",\"on\":\"2026-11-20\"}";

        TimeProbe probe = objectMapper.readValue(json, TimeProbe.class);

        assertThat(probe.at()).isEqualTo(LocalDateTime.of(2026, 11, 17, 19, 5, 12));
        assertThat(probe.on()).isEqualTo(LocalDate.of(2026, 11, 20));
    }
}
