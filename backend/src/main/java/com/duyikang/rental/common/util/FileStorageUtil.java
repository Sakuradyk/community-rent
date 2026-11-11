package com.duyikang.rental.common.util;

import com.duyikang.rental.common.config.FileStorageProperties;
import com.duyikang.rental.common.constant.ErrorCode;
import com.duyikang.rental.common.exception.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 图片落盘工具（需求 FR-S-06）。
 *
 * <p>三件事：<b>校验</b>（空文件 / 大小 / Content-Type / 扩展名白名单）、
 * <b>落盘</b>（{@code {root}/{yyyyMM}/{uuid}.{ext}}）、<b>回传相对路径</b>
 * （{@code /uploads/202611/xxx.png}，前端拼 baseURL 即可显示）。</p>
 *
 * <p><b>为什么丢弃原文件名</b>：原文件名可能含 {@code ../}（路径穿越）、
 * 中文或空格（URL 与磁盘编码问题）、超长（文件系统报错）、重名（覆盖别人的图）。
 * 用 UUID 一次性消除这四类风险，扩展名另存即可。</p>
 *
 * <p>不校验文件魔数（magic bytes）是刻意取舍：需求 §9 只要求白名单校验，
 * 而魔数解析对毕设没有额外答辩价值，见本轮方案决策。</p>
 */
@Component
public class FileStorageUtil {

    private static final Logger log = LoggerFactory.getLogger(FileStorageUtil.class);

    /** 按月分目录，避免单目录文件过多。 */
    private static final DateTimeFormatter MONTH_DIR = DateTimeFormatter.ofPattern("yyyyMM");

    private final FileStorageProperties properties;

    public FileStorageUtil(FileStorageProperties properties) {
        this.properties = properties;
    }

    /**
     * 校验并保存上传文件。
     *
     * @param file 上传的文件，允许为 null（视为未选择文件）
     * @return 相对访问路径、落盘绝对路径与最终字节数
     * @throws BizException 2004 类型不支持 / 2005 超出大小限制
     */
    public StoredFile store(MultipartFile file) {
        validate(file);

        String extension = extensionOf(file.getOriginalFilename());
        String monthDir = LocalDate.now().format(MONTH_DIR);
        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + extension;

        Path rootPath = properties.rootPath();
        try {
            Path targetDir = rootPath.resolve(monthDir);
            Files.createDirectories(targetDir);

            Path target = targetDir.resolve(fileName).normalize();
            // 双保险：文件名由本方法生成，理论上不会越界，仍显式断言一次
            if (!target.startsWith(rootPath)) {
                throw new BizException(ErrorCode.FILE_TYPE_NOT_ALLOWED, "文件路径不合法");
            }

            file.transferTo(target);
            long size = Files.size(target);
            String url = properties.getAccessPath() + "/" + monthDir + "/" + fileName;
            log.info("图片已保存 url={} size={}B", url, size);
            return new StoredFile(url, target, size);
        } catch (IOException e) {
            // 磁盘满、权限不足等属于环境故障，交给兜底 handler 记 error + 5000
            log.error("文件保存失败 originalName={}", file.getOriginalFilename(), e);
            throw new IllegalStateException("文件保存失败", e);
        }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.FILE_TYPE_NOT_ALLOWED, "请选择要上传的文件");
        }

        long maxBytes = properties.getMaxSizeMb() * 1024L * 1024L;
        if (file.getSize() > maxBytes) {
            throw new BizException(ErrorCode.FILE_TOO_LARGE,
                    "文件大小不能超过 " + properties.getMaxSizeMb() + "MB");
        }

        String contentType = file.getContentType();
        if (contentType != null
                && !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new BizException(ErrorCode.FILE_TYPE_NOT_ALLOWED, "仅支持图片文件");
        }

        String extension = extensionOf(file.getOriginalFilename());
        List<String> allowed = properties.getAllowedExt();
        if (extension.isEmpty() || allowed == null || !allowed.contains(extension)) {
            throw new BizException(ErrorCode.FILE_TYPE_NOT_ALLOWED,
                    "仅支持 " + String.join("/", allowed == null ? List.of() : allowed) + " 格式");
        }
    }

    /** 取扩展名并统一小写；无扩展名返回空串。 */
    private String extensionOf(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        int dot = originalFilename.lastIndexOf('.');
        if (dot < 0 || dot == originalFilename.length() - 1) {
            return "";
        }
        return originalFilename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 落盘结果。
     *
     * @param url  相对访问路径，如 {@code /uploads/202611/a1b2c3.png}
     * @param path 磁盘绝对路径
     * @param size 实际字节数
     */
    public record StoredFile(String url, Path path, long size) {
    }
}
