package com.duyikang.rental.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 层基础配置。
 *
 * <p>当前只做一件事：把上传目录映射成静态资源，让 {@code GET /uploads/202611/xxx.png}
 * 能直接返回图片（需求 FR-S-06：本地存 {@code /uploads/{yyyyMM}/}，前端拿相对路径即可显示）。</p>
 *
 * <p>不开 CORS：开发期前端通过 Vite proxy 转发 {@code /api} 与 {@code /uploads}
 * （PROJECT_RULES.md §4.6 F11），同源即可，少一处配置就少一处出错面。</p>
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /** 图片不变，允许浏览器缓存一周。 */
    private static final int CACHE_SECONDS = 7 * 24 * 3600;

    private final FileStorageProperties properties;

    public WebMvcConfig(FileStorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = properties.rootPath().toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        registry.addResourceHandler(properties.getAccessPath() + "/**")
                .addResourceLocations(location)
                .setCachePeriod(CACHE_SECONDS);
    }
}
