package com.duyikang.rental.module.admin.vo;

import java.time.LocalDateTime;

/**
 * 逾期扫描结果（管理端"立即执行逾期扫描"按钮的返回值）。
 *
 * @param updated   本次由 {@code IN_USE} 转 {@code OVERDUE} 的订单数
 * @param scannedAt 扫描时间
 */
public record ScanResultVO(int updated, LocalDateTime scannedAt) {
}
