package com.duyikang.rental.module.message.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.duyikang.rental.common.constant.MessageType;
import com.duyikang.rental.common.exception.BizException;
import com.duyikang.rental.common.result.PageResult;
import com.duyikang.rental.common.constant.ErrorCode;
import com.duyikang.rental.module.message.entity.Message;
import com.duyikang.rental.module.message.mapper.MessageMapper;
import com.duyikang.rental.module.message.service.MessageService;
import com.duyikang.rental.module.message.vo.MessageVO;
import com.duyikang.rental.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 站内消息实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private static final int TITLE_MAX = 64;
    private static final int CONTENT_MAX = 500;
    private static final int PAGE_SIZE_DEFAULT = 10;
    private static final int PAGE_SIZE_MAX = 100;

    private final MessageMapper messageMapper;

    @Override
    public void send(Long userId, String type, String title, String content, String bizType, Long bizId) {
        Message message = new Message();
        message.setUserId(userId);
        message.setType(type);
        message.setTitle(truncate(title, TITLE_MAX));
        message.setContent(truncate(content, CONTENT_MAX));
        message.setBizType(bizType);
        message.setBizId(bizId);
        message.setIsRead(MessageType.UNREAD);
        message.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(message);
    }

    @Override
    public void notify(Long userId, String type, String title, String content, String bizType, Long bizId) {
        if (userId == null) {
            return;
        }
        try {
            send(userId, type, title, content, bizType, bizId);
        } catch (RuntimeException e) {
            // 消息是旁路：发不出去不能把已经成功的下单/结算回滚掉，但必须留下痕迹便于排查
            log.warn("站内消息发送失败（不影响主流程）：userId={}，type={}，title={}", userId, type, title, e);
        }
    }

    @Override
    public PageResult<MessageVO> myList(Integer pageNum, Integer pageSize, Boolean unreadOnly) {
        Long userId = CurrentUser.requireId();
        int page = normalize(pageNum, 1);
        int size = normalize(pageSize, PAGE_SIZE_DEFAULT);

        Page<Message> result = messageMapper.selectPage(new Page<>(page, size),
                Wrappers.<Message>lambdaQuery()
                        .eq(Message::getUserId, userId)
                        .eq(Boolean.TRUE.equals(unreadOnly), Message::getIsRead, MessageType.UNREAD)
                        .orderByDesc(Message::getCreatedAt)
                        .orderByDesc(Message::getId));

        List<MessageVO> list = result.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(list, result.getTotal(), page, size);
    }

    @Override
    public long unreadCount() {
        Long userId = CurrentUser.requireId();
        return messageMapper.selectCount(Wrappers.<Message>lambdaQuery()
                .eq(Message::getUserId, userId)
                .eq(Message::getIsRead, MessageType.UNREAD));
    }

    @Override
    public void markRead(Long id) {
        Long userId = CurrentUser.requireId();
        Message message = messageMapper.selectById(id);
        if (message == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "消息不存在");
        }
        if (!userId.equals(message.getUserId())) {
            throw new BizException(ErrorCode.PERMISSION_DENIED_FOR_RESOURCE, "只能操作自己的消息");
        }
        if (Integer.valueOf(MessageType.READ).equals(message.getIsRead())) {
            return; // 已读就什么都不做（幂等，避免无意义的 UPDATE）
        }
        Message patch = new Message();
        patch.setId(id);
        patch.setIsRead(MessageType.READ);
        patch.setReadTime(LocalDateTime.now());
        messageMapper.updateById(patch);
    }

    @Override
    public int markAllRead() {
        Long userId = CurrentUser.requireId();
        return messageMapper.update(null, Wrappers.<Message>lambdaUpdate()
                .set(Message::getIsRead, MessageType.READ)
                .set(Message::getReadTime, LocalDateTime.now())
                .eq(Message::getUserId, userId)
                .eq(Message::getIsRead, MessageType.UNREAD));
    }

    private MessageVO toVO(Message message) {
        return new MessageVO(
                message.getId(),
                message.getType(),
                MessageType.labelOf(message.getType()),
                message.getTitle(),
                message.getContent(),
                message.getBizType(),
                message.getBizId(),
                Integer.valueOf(MessageType.READ).equals(message.getIsRead()),
                message.getCreatedAt());
    }

    private int normalize(Integer value, int fallback) {
        if (value == null || value < 1) {
            return fallback;
        }
        return Math.min(value, PAGE_SIZE_MAX);
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
