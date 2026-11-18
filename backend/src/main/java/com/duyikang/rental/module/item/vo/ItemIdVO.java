package com.duyikang.rental.module.item.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/**
 * 只返回一个 id 的响应体（发布成功等场景）。
 *
 * <p>为什么不用 {@code Result<Long>}：契约 §0 规定"id 一律以字符串下发"，
 * 直接返回 Long 会被序列化成数字，前端拿到后一旦经过 {@code Number()} 就会丢精度（§6）。
 * 包一层带 {@link ToStringSerializer} 的对象，才能和列表/详情里的 id 保持同一类型。</p>
 */
public record ItemIdVO(@JsonSerialize(using = ToStringSerializer.class) Long id) {
}
