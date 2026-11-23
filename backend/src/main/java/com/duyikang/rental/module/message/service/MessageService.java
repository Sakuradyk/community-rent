package com.duyikang.rental.module.message.service;

import com.duyikang.rental.common.result.PageResult;
import com.duyikang.rental.module.message.vo.MessageVO;

/**
 * 站内消息（M1/M2/M3 的旁路通知，不做实时推送）。
 *
 * <p><b>设计要点</b>：{@link #notify(Long, String, String, String, String, Long)} 与
 * {@link #send} 分开 —— 业务代码一律调 {@code notify}，它<b>永不抛异常</b>。
 * 消息是旁路功能，通知失败不该把已经成功的下单/结算事务回滚掉；
 * 但"消息没发出去"必须留日志，所以是 catch + warn 而不是静默吞。</p>
 */
public interface MessageService {

    /**
     * 直接发送，失败抛异常。<b>仅供测试与内部工具使用</b>，业务代码请用 {@link #notify}。
     */
    void send(Long userId, String type, String title, String content, String bizType, Long bizId);

    /**
     * 业务侧通知：写入一条消息，任何异常都只记日志（不中断业务事务）。
     *
     * @param userId  接收人（为 null 时直接跳过，便于调用方少写判空）
     * @param type    {@code MessageType.ORDER / CREDIT / MAINTAIN / SYSTEM}
     * @param title   标题（≤64 字，超长截断）
     * @param content 正文（≤500 字，超长截断）
     * @param bizType 跳转目标类型（{@code MessageType.BIZ_*}）
     * @param bizId   跳转目标主键
     */
    void notify(Long userId, String type, String title, String content, String bizType, Long bizId);

    /**
     * 我的消息分页（按时间倒序）。
     *
     * @param pageNum    从 1 起，越界归 1
     * @param pageSize   默认 10，上限 100
     * @param unreadOnly true 只看未读
     */
    PageResult<MessageVO> myList(Integer pageNum, Integer pageSize, Boolean unreadOnly);

    /**
     * 当前用户的未读条数。
     */
    long unreadCount();

    /**
     * 标记单条已读（只能标记自己的，别人的返回 3008）。
     */
    void markRead(Long id);

    /**
     * 全部标记已读。
     *
     * @return 本次实际更新条数
     */
    int markAllRead();
}
