package com.duyikang.rental.module.message;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.duyikang.rental.common.constant.ErrorCode;
import com.duyikang.rental.common.constant.MessageType;
import com.duyikang.rental.common.exception.BizException;
import com.duyikang.rental.common.result.PageResult;
import com.duyikang.rental.module.item.entity.Item;
import com.duyikang.rental.module.item.mapper.ItemMapper;
import com.duyikang.rental.module.message.entity.Message;
import com.duyikang.rental.module.message.mapper.MessageMapper;
import com.duyikang.rental.module.message.service.MessageService;
import com.duyikang.rental.module.message.vo.MessageVO;
import com.duyikang.rental.module.order.dto.OrderCancelDTO;
import com.duyikang.rental.module.order.dto.OrderCreateDTO;
import com.duyikang.rental.module.order.dto.OrderQuoteDTO;
import com.duyikang.rental.module.order.entity.RentalOrder;
import com.duyikang.rental.module.order.mapper.RentalOrderMapper;
import com.duyikang.rental.module.order.service.OrderService;
import com.duyikang.rental.module.order.service.OrderStateService;
import com.duyikang.rental.module.settlement.dto.OrderReturnDTO;
import com.duyikang.rental.module.settlement.service.SettlementService;
import com.duyikang.rental.security.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 站内消息集成测试（G2）。
 *
 * <p>验证两件事：①业务事件确实会写消息（谁收到、type / bizType / bizId 正确）
 * ②消息自身的读写语义（未读数、单条已读、全部已读、越权 3008）。</p>
 *
 * <p>断言一律用「做某动作前后的增量」，不锁绝对条数 —— 种子库里已有消息，
 * 且消息会随真实使用增长（坑 53 的同一类问题）。</p>
 */
@SpringBootTest
@Transactional
class MessageIntegrationTest {

    private static final Long OWNER = 2L;
    private static final Long RENTER = 3L;
    private static final Long STRANGER = 4L;

    @Autowired
    private MessageService messageService;
    @Autowired
    private MessageMapper messageMapper;
    @Autowired
    private ItemMapper itemMapper;
    @Autowired
    private RentalOrderMapper rentalOrderMapper;
    @Autowired
    private OrderService orderService;
    @Autowired
    private OrderStateService orderStateService;
    @Autowired
    private SettlementService settlementService;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("下单后出租方收到一条待确认消息，bizId 指向该订单")
    void createOrder_notifiesOwner() {
        Long ownerBefore = unreadOf(OWNER);
        loginAs(RENTER, "李娜", "USER");

        Long itemId = seedOnShelfItem(OWNER);
        OrderCreateDTO dto = new OrderCreateDTO(itemId, LocalDate.now().plusDays(30),
                LocalDate.now().plusDays(32), "消息测试");
        Long orderId = Long.valueOf(orderService.create(dto).id());

        List<Message> messages = messagesOf(OWNER);
        assertThat(unreadOf(OWNER)).isEqualTo(ownerBefore + 1);
        Message latest = messages.get(0);
        assertThat(latest.getType()).isEqualTo(MessageType.ORDER);
        assertThat(latest.getBizType()).isEqualTo(MessageType.BIZ_ORDER);
        assertThat(latest.getBizId()).isEqualTo(orderId);
        assertThat(latest.getTitle()).contains("租赁申请");
    }

    @Test
    @DisplayName("出租方确认后承租方收到消息；取用也会再推一条")
    void confirmAndPickup_notifyRenter() {
        Long itemId = seedOnShelfItem(OWNER);
        Long orderId = createOrderAs(RENTER, itemId, 40);

        Long before = unreadOf(RENTER);
        loginAs(OWNER, "张伟", "USER");
        orderStateService.confirm(orderId);
        orderStateService.pickup(orderId);

        assertThat(unreadOf(RENTER)).isEqualTo(before + 2);
        List<Message> messages = messagesOf(RENTER);
        assertThat(messages.get(0).getBizId()).isEqualTo(orderId);
        assertThat(messages.get(1).getBizId()).isEqualTo(orderId);
    }

    @Test
    @DisplayName("承租方取消订单后出租方收到消息")
    void cancel_notifiesCounterparty() {
        Long itemId = seedOnShelfItem(OWNER);
        Long orderId = createOrderAs(RENTER, itemId, 50);

        Long before = unreadOf(OWNER);
        loginAs(RENTER, "李娜", "USER");
        orderStateService.cancel(orderId, new OrderCancelDTO("临时有事"));

        assertThat(unreadOf(OWNER)).isEqualTo(before + 1);
        Message latest = messagesOf(OWNER).get(0);
        assertThat(latest.getContent()).contains("临时有事");
    }

    @Test
    @DisplayName("承租方申报归还后出租方收到验收提醒")
    void requestReturn_notifiesOwner() {
        Long itemId = seedOnShelfItem(OWNER);
        Long orderId = createOrderAs(RENTER, itemId, 60);
        loginAs(OWNER, "张伟", "USER");
        orderStateService.confirm(orderId);
        orderStateService.pickup(orderId);

        Long before = unreadOf(OWNER);
        loginAs(RENTER, "李娜", "USER");
        settlementService.requestReturn(orderId, new OrderReturnDTO("WORN", "有一道划痕"));

        assertThat(unreadOf(OWNER)).isEqualTo(before + 1);
        Message latest = messagesOf(OWNER).get(0);
        assertThat(latest.getContent()).contains("轻微磨损").contains("验收");
    }

    @Test
    @DisplayName("归还结算后承租方收到结算单消息，bizType 指向结算单")
    void returnItem_notifiesRenter() {
        Long itemId = seedOnShelfItem(OWNER);
        Long orderId = createOrderAs(RENTER, itemId, 70);
        loginAs(OWNER, "张伟", "USER");
        orderStateService.confirm(orderId);
        orderStateService.pickup(orderId);

        Long before = unreadOf(RENTER);
        settlementService.returnItem(orderId, new OrderReturnDTO("INTACT", "完好归还"));

        assertThat(unreadOf(RENTER)).isEqualTo(before + 1);
        Message latest = messagesOf(RENTER).get(0);
        assertThat(latest.getBizType()).isEqualTo(MessageType.BIZ_SETTLEMENT);
        assertThat(latest.getContent()).contains("应退");
    }

    @Test
    @DisplayName("未读数 / 单条已读 / 全部已读 / 只看未读")
    void readOperations_work() {
        loginAs(STRANGER, "王强", "USER");
        long base = messageService.unreadCount();
        messageService.send(STRANGER, MessageType.SYSTEM, "测试一", "正文一", null, null);
        messageService.send(STRANGER, MessageType.SYSTEM, "测试二", "正文二", null, null);
        assertThat(messageService.unreadCount()).isEqualTo(base + 2);

        PageResult<MessageVO> unread = messageService.myList(1, 10, true);
        assertThat(unread.getList()).hasSize((int) base + 2);
        assertThat(unread.getList()).allMatch(vo -> !vo.read());

        Long firstId = unread.getList().get(0).id();
        messageService.markRead(firstId);
        assertThat(messageService.unreadCount()).isEqualTo(base + 1);

        // 幂等：重复标记同一条不报错、不重复扣减
        messageService.markRead(firstId);
        assertThat(messageService.unreadCount()).isEqualTo(base + 1);

        assertThat(messageService.markAllRead()).isEqualTo((int) base + 1);
        assertThat(messageService.unreadCount()).isZero();

        PageResult<MessageVO> all = messageService.myList(1, 10, false);
        assertThat(all.getTotal()).isGreaterThanOrEqualTo(base + 2);
    }

    @Test
    @DisplayName("标记别人的消息已读 → 3008")
    void markRead_byOthers_forbidden() {
        messageService.send(OWNER, MessageType.SYSTEM, "只有张伟能看", "正文", null, null);
        Long ownerMessageId = messagesOf(OWNER).get(0).getId();

        loginAs(RENTER, "李娜", "USER");
        assertThatThrownBy(() -> messageService.markRead(ownerMessageId))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PERMISSION_DENIED_FOR_RESOURCE.getCode()));
    }

    // ---------------- 工具 ----------------

    private Long seedOnShelfItem(Long ownerId) {
        Item item = new Item();
        item.setOwnerId(ownerId);
        item.setCategoryId(8L);
        item.setTitle("消息测试物品" + System.nanoTime());
        item.setDescription("测试用");
        item.setCoverUrl("/uploads/202611/00000000000000000000000000000000.png");
        item.setCommunityId(1L);
        item.setDailyPrice(new BigDecimal("20.00"));
        item.setDeposit(new BigDecimal("100.00"));
        item.setOverdueRate(new BigDecimal("0.5000"));
        item.setLossValue(new BigDecimal("0.0000"));
        item.setStatus("ON_SHELF");
        item.setRentCount(0);
        item.setViewCount(0);
        itemMapper.insert(item);
        return item.getId();
    }

    private Long createOrderAs(Long renterId, Long itemId, int plusDays) {
        loginAs(renterId, "承租人", "USER");
        orderService.quote(new OrderQuoteDTO(itemId, LocalDate.now().plusDays(plusDays),
                LocalDate.now().plusDays(plusDays + 2)));
        return Long.valueOf(orderService.create(new OrderCreateDTO(itemId,
                LocalDate.now().plusDays(plusDays), LocalDate.now().plusDays(plusDays + 2), null)).id());
    }

    /** 该用户全部消息（按时间倒序，与列表接口同序）。 */
    private List<Message> messagesOf(Long userId) {
        return messageMapper.selectList(Wrappers.<Message>lambdaQuery()
                .eq(Message::getUserId, userId)
                .orderByDesc(Message::getCreatedAt)
                .orderByDesc(Message::getId));
    }

    private long unreadOf(Long userId) {
        return messageMapper.selectCount(Wrappers.<Message>lambdaQuery()
                .eq(Message::getUserId, userId)
                .eq(Message::getIsRead, MessageType.UNREAD));
    }

    private void loginAs(Long userId, String nickname, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new LoginUser(userId, nickname, role), null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
}
