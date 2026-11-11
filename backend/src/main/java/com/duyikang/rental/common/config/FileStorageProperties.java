package com.duyikang.rental.common.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 文件存储配置（PROJECT_RULES.md §9「配置外置清单」中的 file.* 四项）。
 *
 * <p>四项均属<b>基础设施配置</b>：它们不改变业务规则，只是把「文件落在哪个盘」告诉程序，
 * 因此走 {@code application-dev.yml}，不进策略表。业务规则类阈值（信誉、费用、损耗、推荐权重）
 * 才必须落在 {@code sys_config} / 策略表里（需求总结 §5）。</p>
 *
 * <p>字段与 yml 的映射：{@code file.root} / {@code file.access-path} /
 * {@code file.max-size-mb} / {@code file.allowed-ext}。</p>
 */
@Component
@ConfigurationProperties(prefix = "file")
public class FileStorageProperties {

    private static final Logger log = LoggerFactory.getLogger(FileStorageProperties.class);

    /** 物理存储根目录，建议在开发环境用绝对路径，部署时改成服务器目录。 */
    private String root = "uploads";

    /** 对外访问前缀，与 {@code WebMvcConfig} 的静态资源映射保持一致。 */
    private String accessPath = "/uploads";

    /** 单文件大小上限（MB），需与 {@code spring.servlet.multipart.max-file-size} 保持一致。 */
    private long maxSizeMb = 5;

    /** 允许的扩展名白名单（一律小写，不含点）。 */
    private List<String> allowedExt = List.of("jpg", "jpeg", "png", "webp");

    /**
     * 启动即建目录：首次上传时才建目录会让第一个用户吃到 500，
     * 而且错误发生在业务代码里、排查成本高。
     */
    @PostConstruct
    public void initRootDir() {
        try {
            Files.createDirectories(rootPath());
            log.info("文件存储根目录就绪：{}，单文件上限 {}MB，允许扩展名 {}",
                    rootPath(), maxSizeMb, allowedExt);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建文件存储根目录：" + rootPath(), e);
        }
    }

    /** 归一化后的绝对路径，所有落盘操作都以它为基准做越界校验。 */
    public Path rootPath() {
        return Paths.get(root).toAbsolutePath().normalize();
    }

    public String getRoot() {
        return root;
    }

    public void setRoot(String root) {
        this.root = root;
    }

    public String getAccessPath() {
        return accessPath;
    }

    public void setAccessPath(String accessPath) {
        this.accessPath = accessPath;
    }

    public long getMaxSizeMb() {
        return maxSizeMb;
    }

    public void setMaxSizeMb(long maxSizeMb) {
        this.maxSizeMb = maxSizeMb;
    }

    public List<String> getAllowedExt() {
        return allowedExt;
    }

    public void setAllowedExt(List<String> allowedExt) {
        this.allowedExt = allowedExt;
    }
}
