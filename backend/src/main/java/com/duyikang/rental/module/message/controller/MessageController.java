package com.duyikang.rental.module.message.controller;

import com.duyikang.rental.common.result.PageResult;
import com.duyikang.rental.common.result.Result;
import com.duyikang.rental.module.message.service.MessageService;
import com.duyikang.rental.module.message.vo.MessageVO;
import com.duyikang.rental.module.message.vo.UnreadCountVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 站内消息：我的消息列表、未读数、标记已读。
 *
 * <p>全部需要登录（不放进 SecurityConfig 的白名单），且只能看/改自己的消息 ——
 * 用户身份一律从 SecurityContextHolder 取，不接受请求参数里的 userId。</p>
 */
@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    /**
     * 我的消息分页（时间倒序）。
     */
    @GetMapping
    public Result<PageResult<MessageVO>> list(@RequestParam(required = false) Integer pageNum,
                                              @RequestParam(required = false) Integer pageSize,
                                              @RequestParam(required = false) Boolean unreadOnly) {
        return Result.ok(messageService.myList(pageNum, pageSize, unreadOnly));
    }

    /**
     * 未读数（顶栏铃铛）。
     */
    @GetMapping("/unread-count")
    public Result<UnreadCountVO> unreadCount() {
        return Result.ok(new UnreadCountVO(messageService.unreadCount()));
    }

    /**
     * 标记单条已读（幂等）。
     */
    @PostMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id) {
        messageService.markRead(id);
        return Result.ok();
    }

    /**
     * 全部标记已读，返回本次实际更新条数（前端据此提示「已标记 N 条」）。
     */
    @PostMapping("/read-all")
    public Result<Integer> markAllRead() {
        return Result.ok(messageService.markAllRead());
    }
}
