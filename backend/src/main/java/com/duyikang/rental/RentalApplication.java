package com.duyikang.rental;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 社区物品租用平台 · 启动类。
 *
 * <p>单模块 Maven 工程，按包分层表达四层架构（见 PROJECT_RULES.md §4.1）。</p>
 */
@SpringBootApplication
@EnableScheduling
@MapperScan("com.duyikang.rental.**.mapper")
public class RentalApplication {

    public static void main(String[] args) {
        SpringApplication.run(RentalApplication.class, args);
    }
}
