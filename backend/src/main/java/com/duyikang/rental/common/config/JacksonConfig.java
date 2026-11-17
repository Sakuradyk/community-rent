package com.duyikang.rental.common.config;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 时间字段的 JSON 契约（对应 docs/接口文档.md §0 的「时间」一行）。
 *
 * <p>为什么需要这个类：{@code spring.jackson.date-format} 只作用于 {@link java.util.Date}，
 * 对 {@link LocalDate}/{@link LocalDateTime} 无效，默认会序列化成 {@code 2026-11-16T20:36:43}
 * 这种 ISO 串。实测注册接口返回过 ISO 格式，与契约不符，故在此统一格式。
 *
 * <p>前端只认两种格式：时刻 {@code yyyy-MM-dd HH:mm:ss}，日期 {@code yyyy-MM-dd}。
 * 反序列化也按同一格式配置，避免前端传回的日期串解析失败。
 */
@Configuration
public class JacksonConfig {

    /** 时刻格式，与 docs/接口文档.md §0 一致 */
    public static final String DATETIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    /** 日期格式，与 docs/接口文档.md §0 一致 */
    public static final String DATE_PATTERN = "yyyy-MM-dd";

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer javaTimeCustomizer() {
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(DATETIME_PATTERN);
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern(DATE_PATTERN);

        SimpleModule module = new SimpleModule();
        module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(dateTimeFormatter));
        module.addSerializer(LocalDate.class, new LocalDateSerializer(dateFormatter));
        module.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(dateTimeFormatter));
        module.addDeserializer(LocalDate.class, new LocalDateDeserializer(dateFormatter));

        return builder -> builder.modulesToInstall(module);
    }
}
