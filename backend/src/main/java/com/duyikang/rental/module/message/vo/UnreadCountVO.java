package com.duyikang.rental.module.message.vo;

/**
 * 未读数（顶栏铃铛用）。
 *
 * <p>单独一个接口而不是塞进列表响应：铃铛要每页都查，列表只在消息页查；
 * 两者的调用频率差一个数量级，合并会让每次翻页都白算未读。</p>
 *
 * @param count 未读条数
 */
public record UnreadCountVO(long count) {
}
