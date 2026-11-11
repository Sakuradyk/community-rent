package com.duyikang.rental.module.system.controller;

import com.duyikang.rental.common.result.Result;
import com.duyikang.rental.common.util.FileStorageUtil;
import com.duyikang.rental.module.system.vo.UploadVO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传接口。
 *
 * <p>需要登录（SecurityConfig 默认 {@code anyRequest().authenticated()}），
 * 未登录返回 1001。校验规则全部在 {@link FileStorageUtil} 内，
 * Controller 只做参数接收与结果封装（PROJECT_RULES.md §4.3）。</p>
 */
@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileStorageUtil fileStorageUtil;

    public FileController(FileStorageUtil fileStorageUtil) {
        this.fileStorageUtil = fileStorageUtil;
    }

    /**
     * 上传单张图片。
     *
     * @param file 表单字段名固定为 {@code file}
     * @return {@code data.url} 为相对路径，前端拼上 baseURL 即得可访问地址
     */
    @PostMapping("/upload")
    public Result<UploadVO> upload(@RequestParam("file") MultipartFile file) {
        FileStorageUtil.StoredFile stored = fileStorageUtil.store(file);
        return Result.ok(new UploadVO(stored.url(), stored.size()));
    }
}
