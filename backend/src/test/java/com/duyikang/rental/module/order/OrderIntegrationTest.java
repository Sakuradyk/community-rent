package com.duyikang.rental.module.order;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.duyikang.rental.common.constant.ErrorCode;
import com.duyikang.rental.common.exception.BizException;
import com.duyikang.rental.common.result.PageResult;
import com.duyikang.rental.module.order.dto.OrderCreateDTO;
import com.duyikang.rental.module.order.dto.OrderQuoteDTO;
import com.duyikang.rental.module.order.entity.OrderOccupancy;
import com.duyikang.rental.module.order.entity.RentalOrder;
import com.duyikang.rental.module.order.mapper.OrderOccupancyMapper;
import com.duyikang.rental.module.order.mapper.RentalOrderMapper;
import com.duyikang.rental.module.order.service.OrderService;
import com.duyikang.rental.module.order.vo.OrderCreatedVO;
import com.duyikang.rental.module.order.vo.OrderDetailVO;
import com.duyikang.rental.module.order.vo.OrderListItemVO;
import com.duyikang.rental.module.order.vo.OrderQuoteVO;
import com.duyikang.rental.module.item.entity.Item;
import com.duyikang.rental.module.item.mapper.ItemMapper;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 切片 3 订单（试算 / 下单 / 详情）集成测试。
 *
 * <p>连真实库、{@code @Transactional} 自动回滚；日期一律相对 {@code LocalDate.now()} 生成，
 * 避免"种子里的 11-20 过两天变成过去日期"导致测试自行过期。</p>
 *
 * <p>种子事实（`backend/sql/02-data.sql`）：物品 1 = 张伟(2) 的帐篷 25.00/100.00/0.5000 在架；
 * 物品 3 = 李娜(3) 的电钻 15.00/80.00/0.5000 在架；物品 6 = 王强(4) 的耳机 待审核；
 * 用户 1 管理员 / 2 张伟 / 3 李娜 / 4 王强。</p>
 */
@SpringBootTest
@Transactional
class OrderIntegrationTest {

    private static final Long OWNER_ZHANGWEI = 2L;
    private static final Long RENTER_LINA = 3L;
    private static final Long WANGQIANG = 4L;
    private static final Long ADMIN = 1L;

    private static final Long ITEM_ZHANGWEI_TENT = 1L;
    private static final Long ITEM_LINA_DRILL = 3L;

    @Autowired
    private OrderService orderService;
    @Autowired
    private RentalOrderMapper rentalOrderMapper;
    @Autowired
    private OrderOccupancyMapper orderOccupancyMapper;
    @Autowired
    private ItemMapper itemMapper;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------ 试算

    @Test
    @DisplayName("试算：无冲突时按 日租金 × 天数 算钱，总额 = 租金 + 押金")
    void quote_noConflict_calculatesAmounts() {
        loginAs(RENTER_LINA, "李娜", "USER");
        LocalDate start = LocalDate.now().plusDays(2);
        LocalDate end = start.plusDays(3);

        OrderQuoteVO vo = orderService.quote(new OrderQuoteDTO(ITEM_ZHANGWEI_TENT, start, end));

        assertThat(vo.rentDays()).isEqualTo(3);
        assertThat(vo.dailyPrice()).isEqualByComparingTo("25.00");
        assertThat(vo.rentAmount()).isEqualByComparingTo("75.00");
        assertThat(vo.depositAmount()).isEqualByComparingTo("100.00");
        assertThat(vo.totalAmount()).isEqualByComparingTo("175.00");
        assertThat(vo.overdueRate()).isEqualByComparingTo("0.5000");
    }

    @Test
    @DisplayName("试算：别人已下单的时段 → 4090，且 data 带冲突日期与候选时段")
    void quote_conflictingDates_throwsOccupancyConflictWithSuggestions() {
        loginAs(RENTER_LINA, "李娜", "USER");
        LocalDate start = LocalDate.now().plusDays(4);
        LocalDate end = start.plusDays(3);
        orderService.create(new OrderCreateDTO(ITEM_ZHANGWEI_TENT, start, end, "李娜先下单"));

        loginAs(WANGQIANG, "王强", "USER");
        assertThatThrownBy(() -> orderService.quote(new OrderQuoteDTO(ITEM_ZHANGWEI_TENT, start, end)))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.OCCUPANCY_CONFLICT.getCode());
                    Map<String, Object> data = asMap(e.getData());
                    assertThat(data.get("itemId")).isEqualTo(String.valueOf(ITEM_ZHANGWEI_TENT));
                    @SuppressWarnings("unchecked")
                    List<String> conflictDates = (List<String>) data.get("conflictDates");
                    assertThat(conflictDates).hasSize(3)
                            .containsExactly(start.toString(), start.plusDays(1).toString(), start.plusDays(2).toString());
                    @SuppressWarnings("unchecked")
                    List<Map<String, String>> suggestRanges = (List<Map<String, String>>) data.get("suggestRanges");
                    assertThat(suggestRanges).isNotEmpty().hasSizeLessThanOrEqualTo(3);
                    assertThat(suggestRanges.get(0)).containsKeys("startDate", "endDate");
                });
    }

    @Test
    @DisplayName("试算：租自己的物品 → 3006（自租优先于状态判断）")
    void quote_ownItem_throwsSelfRentForbidden() {
        loginAs(OWNER_ZHANGWEI, "张伟", "USER");
        LocalDate start = LocalDate.now().plusDays(2);
        assertThatThrownBy(() -> orderService.quote(
                new OrderQuoteDTO(ITEM_ZHANGWEI_TENT, start, start.plusDays(1))))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.SELF_RENT_FORBIDDEN.getCode()));
    }

    @Test
    @DisplayName("试算：待审核物品对非发布者不可见 → 3004")
    void quote_pendingItem_invisibleToOthers() {
        Long pendingId = seedPendingItem(WANGQIANG);

        loginAs(RENTER_LINA, "李娜", "USER");
        LocalDate start = LocalDate.now().plusDays(2);
        assertThatThrownBy(() -> orderService.quote(
                new OrderQuoteDTO(pendingId, start, start.plusDays(1))))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.ITEM_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("试算：待审核物品对发布者可见，但仍是自租 → 3006")
    void quote_pendingItem_asOwner_stillSelfRent() {
        Long pendingId = seedPendingItem(WANGQIANG);

        loginAs(WANGQIANG, "王强", "USER");
        LocalDate start = LocalDate.now().plusDays(2);
        assertThatThrownBy(() -> orderService.quote(
                new OrderQuoteDTO(pendingId, start, start.plusDays(1))))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.SELF_RENT_FORBIDDEN.getCode()));
    }

    @Test
    @DisplayName("试算：起租日早于今天 → 2001")
    void quote_pastStartDate_throwsParamError() {
        loginAs(RENTER_LINA, "李娜", "USER");
        LocalDate start = LocalDate.now().minusDays(1);
        assertThatThrownBy(() -> orderService.quote(
                new OrderQuoteDTO(ITEM_ZHANGWEI_TENT, start, start.plusDays(2))))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode()));
    }

    // ------------------------------------------------------------------ 下单

    @Test
    @DisplayName("下单：成功后状态 PENDING、订单号格式正确、占用按天展开、金额快照")
    void create_success_writesOrderAndExpandsOccupancyPerDay() {
        loginAs(OWNER_ZHANGWEI, "张伟", "USER");
        LocalDate start = LocalDate.now().plusDays(10);
        LocalDate end = start.plusDays(3);

        OrderCreatedVO created = orderService.create(new OrderCreateDTO(ITEM_LINA_DRILL, start, end, "周末修柜子"));

        assertThat(created.orderNo()).matches("^R\\d{8}\\d{4}$");
        assertThat(created.status()).isEqualTo("PENDING");
        assertThat(created.statusLabel()).isEqualTo("待确认");
        assertThat(created.rentDays()).isEqualTo(3);
        assertThat(created.rentAmount()).isEqualByComparingTo("45.00");
        assertThat(created.depositAmount()).isEqualByComparingTo("80.00");
        assertThat(created.totalAmount()).isEqualByComparingTo("125.00");

        RentalOrder saved = rentalOrderMapper.selectById(Long.valueOf(created.id()));
        assertThat(saved).isNotNull();
        assertThat(saved.getOwnerId()).isEqualTo(RENTER_LINA);      // 出租方来自物品
        assertThat(saved.getRenterId()).isEqualTo(OWNER_ZHANGWEI);  // 承租方是下单人
        assertThat(saved.getDailyPrice()).isEqualByComparingTo("15.00");
        assertThat(saved.getDeposit()).isEqualByComparingTo("80.00");
        assertThat(saved.getOverdueRate()).isEqualByComparingTo("0.5000");

        List<OrderOccupancy> rows = orderOccupancyMapper.selectList(
                Wrappers.<OrderOccupancy>lambdaQuery().eq(OrderOccupancy::getOrderId, saved.getId()));
        assertThat(rows).hasSize(3);
        assertThat(rows).extracting(OrderOccupancy::getOccupyDate)
                .containsExactlyInAnyOrder(start, start.plusDays(1), start.plusDays(2));
    }

    @Test
    @DisplayName("下单：同一物品同一时段第二次下单 → 4090（唯一索引/预检任一拦下都算通过）")
    void create_sameSlotTwice_throwsOccupancyConflict() {
        LocalDate start = LocalDate.now().plusDays(15);
        LocalDate end = start.plusDays(2);

        loginAs(OWNER_ZHANGWEI, "张伟", "USER");
        orderService.create(new OrderCreateDTO(ITEM_LINA_DRILL, start, end, null));

        loginAs(WANGQIANG, "王强", "USER");
        assertThatThrownBy(() -> orderService.create(new OrderCreateDTO(ITEM_LINA_DRILL, start, end, null)))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.OCCUPANCY_CONFLICT.getCode()));
    }

    @Test
    @DisplayName("下单：超过最大租期（30 天）→ 2001")
    void create_rentDaysOverLimit_throwsParamError() {
        loginAs(OWNER_ZHANGWEI, "张伟", "USER");
        LocalDate start = LocalDate.now().plusDays(2);
        assertThatThrownBy(() -> orderService.create(
                new OrderCreateDTO(ITEM_LINA_DRILL, start, start.plusDays(31), null)))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode()));
    }

    @Test
    @DisplayName("下单：归还日不晚于起租日 → 2001")
    void create_endNotAfterStart_throwsParamError() {
        loginAs(OWNER_ZHANGWEI, "张伟", "USER");
        LocalDate start = LocalDate.now().plusDays(2);
        assertThatThrownBy(() -> orderService.create(
                new OrderCreateDTO(ITEM_LINA_DRILL, start, start, null)))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode()));
    }

    @Test
    @DisplayName("下单：物品在架状态不变，占用只体现在 order_occupancy")
    void create_doesNotChangeItemStatus() {
        LocalDate start = LocalDate.now().plusDays(20);
        loginAs(OWNER_ZHANGWEI, "张伟", "USER");
        orderService.create(new OrderCreateDTO(ITEM_LINA_DRILL, start, start.plusDays(1), null));

        Item item = itemMapper.selectById(ITEM_LINA_DRILL);
        assertThat(item.getStatus()).isEqualTo("ON_SHELF");

        List<LocalDate> occupied = orderOccupancyMapper.selectOccupiedDates(
                ITEM_LINA_DRILL, start, start.plusDays(1));
        assertThat(occupied).containsExactly(start);
    }

    // ------------------------------------------------------------------ 详情

    @Test
    @DisplayName("详情：双边可见，第三方 3008，管理员可见")
    void detail_visibleToBilateralAndAdmin_only() {
        loginAs(OWNER_ZHANGWEI, "张伟", "USER");
        LocalDate start = LocalDate.now().plusDays(25);
        OrderCreatedVO created = orderService.create(new OrderCreateDTO(ITEM_LINA_DRILL, start, start.plusDays(2), "取货"));

        OrderDetailVO asRenter = orderService.detail(Long.valueOf(created.id()));
        assertThat(asRenter.orderNo()).isEqualTo(created.orderNo());
        assertThat(asRenter.renter().nickname()).isEqualTo("张伟");
        assertThat(asRenter.owner().nickname()).isEqualTo("李娜");
        assertThat(asRenter.itemTitle()).isNotBlank();
        assertThat(asRenter.statusLabel()).isEqualTo("待确认");

        loginAs(RENTER_LINA, "李娜", "USER");
        assertThat(orderService.detail(Long.valueOf(created.id())).orderNo()).isEqualTo(created.orderNo());

        loginAs(WANGQIANG, "王强", "USER");
        assertThatThrownBy(() -> orderService.detail(Long.valueOf(created.id())))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PERMISSION_DENIED_FOR_RESOURCE.getCode()));

        loginAs(ADMIN, "平台管理员", "ADMIN");
        assertThat(orderService.detail(Long.valueOf(created.id())).orderNo()).isEqualTo(created.orderNo());
    }

    @Test
    @DisplayName("详情：订单不存在 → 3005")
    void detail_notFound_throwsOrderNotFound() {
        loginAs(RENTER_LINA, "李娜", "USER");
        assertThatThrownBy(() -> orderService.detail(999_999L))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.ORDER_NOT_FOUND.getCode()));
    }

    // ------------------------------------------------------------------ 我的订单列表

    @Test
    @DisplayName("列表：承租人视角能查到刚下的单，role=renter，对方昵称是出租方")
    void list_asRenter_containsNewOrder() {
        loginAs(OWNER_ZHANGWEI, "张伟", "USER");
        LocalDate start = LocalDate.now().plusDays(30);
        OrderCreatedVO created = orderService.create(new OrderCreateDTO(ITEM_LINA_DRILL, start, start.plusDays(2), null));

        PageResult<OrderListItemVO> page = orderService.list("renter", null, 1, 100);

        assertThat(page.getList()).extracting(OrderListItemVO::id).contains(created.id());
        OrderListItemVO row = page.getList().stream()
                .filter(o -> o.id().equals(created.id())).findFirst().orElseThrow();
        assertThat(row.role()).isEqualTo("renter");
        assertThat(row.counterpartyNickname()).isEqualTo("李娜");
        assertThat(row.status()).isEqualTo("PENDING");
        assertThat(row.statusLabel()).isEqualTo("待确认");
        // start.plusDays(2) 表示租 2 天（半开区间），2 × 15.00 + 80.00 押金 = 110.00`n        assertThat(row.totalAmount()).isEqualByComparingTo("110.00");
        assertThat(row.itemTitle()).isNotBlank();
    }

    @Test
    @DisplayName("列表：出租方视角 role=owner，对方昵称是承租人；承租人视角查不到这层的 owner 身份")
    void list_asOwner_roleIsOwner() {
        loginAs(OWNER_ZHANGWEI, "张伟", "USER");
        LocalDate start = LocalDate.now().plusDays(35);
        OrderCreatedVO created = orderService.create(new OrderCreateDTO(ITEM_LINA_DRILL, start, start.plusDays(1), null));

        loginAs(RENTER_LINA, "李娜", "USER");
        PageResult<OrderListItemVO> asOwner = orderService.list("owner", null, 1, 100);
        OrderListItemVO row = asOwner.getList().stream()
                .filter(o -> o.id().equals(created.id())).findFirst().orElseThrow();
        assertThat(row.role()).isEqualTo("owner");
        assertThat(row.counterpartyNickname()).isEqualTo("张伟");

        // 同一笔订单在「我租的」里不应该出现（李娜是出租方）
        assertThat(orderService.list("renter", null, 1, 100).getList())
                .extracting(OrderListItemVO::id).doesNotContain(created.id());
    }

    @Test
    @DisplayName("列表：status 过滤只返回该状态；all 视角两人都看得到")
    void list_statusFilter_andAll() {
        loginAs(OWNER_ZHANGWEI, "张伟", "USER");
        LocalDate start = LocalDate.now().plusDays(40);
        OrderCreatedVO created = orderService.create(new OrderCreateDTO(ITEM_LINA_DRILL, start, start.plusDays(1), null));

        PageResult<OrderListItemVO> pending = orderService.list("all", "PENDING", 1, 100);
        assertThat(pending.getList()).allMatch(o -> "PENDING".equals(o.status()));
        assertThat(pending.getList()).extracting(OrderListItemVO::id).contains(created.id());

        assertThat(orderService.list("all", "COMPLETED", 1, 100).getList())
                .extracting(OrderListItemVO::id).doesNotContain(created.id());

        // 分页口径：pageSize 上限 100，传 0/负数按默认走，不抛异常
        assertThat(orderService.list("all", null, 0, 0).getPageNum()).isEqualTo(1);
    }

    @Test
    @DisplayName("列表：未登录 → 1001")
    void list_anonymous_throwsUnauthorized() {
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> orderService.list("renter", null, 1, 10))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.UNAUTHORIZED.getCode()));
    }

    // ------------------------------------------------------------------ 工具

    /**
     * 直接插一件待审核物品（跳过审核流程），供"可见性"类断言使用。
     *
     * <p>不依赖种子里的 item 6：用户会在浏览器里把它审核掉，断言随之过期。</p>
     */
    private Long seedPendingItem(Long ownerId) {
        Item item = new Item();
        item.setOwnerId(ownerId);
        item.setCategoryId(8L);       // 帐篷（二级）
        item.setCommunityId(1L);      // 阳光花园
        item.setTitle("待审核测试物品");
        item.setDescription("仅测试用");
        item.setCoverUrl("/uploads/202611/" + "0".repeat(32) + ".png");
        item.setDailyPrice(new java.math.BigDecimal("12.00"));
        item.setDeposit(new java.math.BigDecimal("50.00"));
        item.setOverdueRate(new java.math.BigDecimal("0.5000"));
        item.setLossValue(java.math.BigDecimal.ZERO);
        item.setStatus("PENDING");
        itemMapper.insert(item);
        return item.getId();
    }

    private void loginAs(Long userId, String nickname, String role) {
        LoginUser user = new LoginUser(userId, nickname, role);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object data) {
        assertThat(data).isInstanceOf(Map.class);
        return (Map<String, Object>) data;
    }

}
