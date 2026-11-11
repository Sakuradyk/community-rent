package com.duyikang.rental.module.system.vo;

/**
 * 上传结果。
 *
 * @param url  相对访问路径，如 {@code /uploads/202611/a1b2c3d4.png}
 * @param size 文件字节数
 */
public record UploadVO(String url, long size) {
}
