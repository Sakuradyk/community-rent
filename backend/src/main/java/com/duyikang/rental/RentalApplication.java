package com.duyikang.rental;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 社区物品租用平台 · 启动类。
 *
 * <p>单模块 Maven 工程，按包分层表达四层架构（见 PROJECT_RULES.md §4.1）。</p>
 */
@Slf4j
@SpringBootApplication
@EnableScheduling
@MapperScan("com.duyikang.rental.**.mapper")
public class RentalApplication {

    public static void main(String[] args) {
        // 先拿上下文再打印：端口写在 yml 里，日志里直接给出实际绑定端口，避免"以为起在 8080"
        var context = SpringApplication.run(RentalApplication.class, args);
        Environment env = context.getEnvironment();
        log.info("应用启动完成：profile={}，端口={}",
                String.join(",", env.getActiveProfiles()), env.getProperty("server.port", "8080"));
    }
}
