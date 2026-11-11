package com.duyikang.rental.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置。
 *
 * <p>分页插件必须显式注册：未注册时 {@code Page} 参数会被静默忽略，
 * SQL 退化成不带 LIMIT 的全表查询——不报错，只是慢，非常难发现。</p>
 */
@Configuration
public class MybatisPlusConfig {

    /** 单页最大条数，与规范 §7.1「pageSize 上限 100」保持一致。 */
    private static final long PAGE_MAX_LIMIT = 100L;

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(PAGE_MAX_LIMIT);
        // 页码越界时返回空页，而不是自动回到第一页：否则前端"翻过头"会看到重复数据
        pagination.setOverflow(false);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}
