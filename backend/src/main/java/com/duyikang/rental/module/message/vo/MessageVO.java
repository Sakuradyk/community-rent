package com.duyikang.rental.module.message.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

/**
 * 消息列表项。
 *
 * @param id        消息主键
 * @param type      类型（ORDER / CREDIT / MAINTAIN / SYSTEM）
 * @param typeLabel 类型中文名（后端给，前端不维护第二份字典）
 * @param title     标题
 * @param content   正文
 * @param bizType   业务类型（ORDER / SETTLEMENT / ITEM），决定点击后跳哪一页
 * @param bizId     业务主键（与 bizType 配对）
 * @param read      是否已读
 * @param createdAt 发送时间
 */
public record MessageVO(@JsonSerialize(using = ToStringSerializer.class) Long id,
                        String type,
                        String typeLabel,
                        String title,
                        String content,
                        String bizType,
                        @JsonSerialize(using = ToStringSerializer.class) Long bizId,
                        boolean read,
                        LocalDateTime createdAt) {
}
