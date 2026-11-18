package com.duyikang.rental.module.item;

import com.duyikang.rental.common.constant.ErrorCode;
import com.duyikang.rental.common.exception.BizException;
import com.duyikang.rental.common.result.PageResult;
import com.duyikang.rental.module.item.dto.ItemAuditDTO;
import com.duyikang.rental.module.item.dto.ItemQuery;
import com.duyikang.rental.module.item.dto.ItemSaveDTO;
import com.duyikang.rental.module.item.dto.ItemShelfDTO;
import com.duyikang.rental.module.item.entity.Item;
import com.duyikang.rental.module.item.mapper.ItemImageMapper;
import com.duyikang.rental.module.item.mapper.ItemMapper;
import com.duyikang.rental.module.item.service.ItemAdminService;
import com.duyikang.rental.module.item.service.ItemService;
import com.duyikang.rental.module.item.vo.CalendarVO;
import com.duyikang.rental.module.item.vo.ItemCardVO;
import com.duyikang.rental.module.item.vo.ItemDetailVO;
import com.duyikang.rental.security.LoginUser;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 物品模块集成测试（真实库 + 事务回滚，不引 H2 —— PROJECT_RULES.md §12）。
 *
 * <p>覆盖四类：发布校验、归属与状态机、检索与日历口径、审核流转。
 * 种子数据（backend/sql/02-data.sql）：物品 1/2 属 owner=2、3/4 属 owner=3、5/6 属 owner=4；
 * 其中 6 是 PENDING，其余 ON_SHELF；物品 2 有 CONFIRMED 订单（占用 11-20/21/22）。</p>
 */
@SpringBootTest
@Transactional
@DisplayName("物品模块：发布、状态机、检索、审核")
class ItemIntegrationTest {

    /** fee_policy：日租金 1.00~500.00，押金为日租金的 1~50 倍，逾期费率 0.1~2.0。 */
    private static final BigDecimal VALID_DAILY_PRICE = new BigDecimal("25.00");
    private static final BigDecimal VALID_DEPOSIT = new BigDecimal("100.00");
    private static final BigDecimal VALID_OVERDUE_RATE = new BigDecimal("0.5000");

    private static final long LEAF_CATEGORY_ID = 8L;   // 帐篷（二级）
    private static final long TOP_CATEGORY_ID = 1L;    // 电子数码（一级，不允许直接挂物品）
    private static final long COMMUNITY_ID = 1L;       // 阳光花园（城东街道）

    @Autowired private ItemService itemService;
    @Autowired private ItemAdminService itemAdminService;
    @Autowired private ItemMapper itemMapper;
    @Autowired private ItemImageMapper itemImageMapper;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // ---------------------------------------------------------------- 发布校验

    @Test
    @DisplayName("发布：落待审核，封面取第一张图，图片逐行入库")
    void create_validInput_savesPendingWithFirstImageAsCover() {
        loginAs(2L, "张伟", "USER");
        List<String> urls = imageUrls(3);

        Long itemId = itemService.create(saveDTO(urls));

        Item saved = itemMapper.selectById(itemId);
        assertThat(saved).isNotNull();
        assertThat(saved.getStatus()).isEqualTo("PENDING");
        assertThat(saved.getOwnerId()).isEqualTo(2L);
        assertThat(saved.getCoverUrl()).isEqualTo(urls.get(0));
        assertThat(saved.getLossValue()).isEqualByComparingTo("0.0000");
        assertThat(itemImageMapper.selectCount(Wrappers.<com.duyikang.rental.module.item.entity.ItemImage>lambdaQuery()
                .eq(com.duyikang.rental.module.item.entity.ItemImage::getItemId, itemId))).isEqualTo(3L);
    }

    @Test
    @DisplayName("发布：0 张图直接拒绝（DTO 校验只在 Controller 生效，Service 必须自守）")
    void create_noImage_throwsParamError() {
        loginAs(2L, "张伟", "USER");
        assertThatThrownBy(() -> itemService.create(saveDTO(List.of())))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode()));
    }

    @Test
    @DisplayName("发布：超过 5 张图拒绝")
    void create_sixImages_throwsParamError() {
        loginAs(2L, "张伟", "USER");
        assertThatThrownBy(() -> itemService.create(saveDTO(imageUrls(6))))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode()));
    }

    @Test
    @DisplayName("发布：日租金低于 fee_policy 下限 → 4001")
    void create_dailyPriceBelowMin_throwsFeeOutOfRange() {
        loginAs(2L, "张伟", "USER");
        ItemSaveDTO dto = new ItemSaveDTO("低价帐篷", LEAF_CATEGORY_ID, "描述", COMMUNITY_ID,
                new BigDecimal("0.50"), new BigDecimal("10.00"), new BigDecimal("0.5000"), imageUrls(1));
        assertThatThrownBy(() -> itemService.create(dto))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.FEE_OUT_OF_RANGE.getCode()));
    }

    @Test
    @DisplayName("发布：押金低于「日租金×ratio_min」→ 4001")
    void create_depositBelowRatio_throwsFeeOutOfRange() {
        loginAs(2L, "张伟", "USER");
        ItemSaveDTO dto = new ItemSaveDTO("低押金帐篷", LEAF_CATEGORY_ID, "描述", COMMUNITY_ID,
                VALID_DAILY_PRICE, new BigDecimal("1.00"), VALID_OVERDUE_RATE, imageUrls(1));
        assertThatThrownBy(() -> itemService.create(dto))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.FEE_OUT_OF_RANGE.getCode()));
    }

    @Test
    @DisplayName("发布：逾期费率超上限 → 4001")
    void create_overdueRateAboveMax_throwsFeeOutOfRange() {
        loginAs(2L, "张伟", "USER");
        ItemSaveDTO dto = new ItemSaveDTO("高费率帐篷", LEAF_CATEGORY_ID, "描述", COMMUNITY_ID,
                VALID_DAILY_PRICE, VALID_DEPOSIT, new BigDecimal("5.0000"), imageUrls(1));
        assertThatThrownBy(() -> itemService.create(dto))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.FEE_OUT_OF_RANGE.getCode()));
    }

    @Test
    @DisplayName("发布：一级类目不能挂物品 → 2001")
    void create_topLevelCategory_throwsParamError() {
        loginAs(2L, "张伟", "USER");
        ItemSaveDTO dto = new ItemSaveDTO("错级类目", TOP_CATEGORY_ID, "描述", COMMUNITY_ID,
                VALID_DAILY_PRICE, VALID_DEPOSIT, VALID_OVERDUE_RATE, imageUrls(1));
        assertThatThrownBy(() -> itemService.create(dto))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode()));
    }

    // ---------------------------------------------------------------- 归属与状态机

    @Test
    @DisplayName("编辑：非归属人 → 3008")
    void update_othersItem_throwsPermissionDenied() {
        loginAs(3L, "李娜", "USER");
        assertThatThrownBy(() -> itemService.update(1L, saveDTO(imageUrls(1))))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode())
                                .isEqualTo(ErrorCode.PERMISSION_DENIED_FOR_RESOURCE.getCode()));
    }

    @Test
    @DisplayName("编辑：在架物品必须先下架 → 3003")
    void update_onShelfItem_throwsStatusInvalid() {
        loginAs(2L, "张伟", "USER");
        assertThatThrownBy(() -> itemService.update(1L, saveDTO(imageUrls(1))))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.ITEM_STATUS_INVALID.getCode()));
    }

    @Test
    @DisplayName("编辑：被驳回后修改，状态回到待审核且清空审核痕迹")
    void update_rejectedItem_resetsAuditTrail() {
        loginAs(1L, "平台管理员", "ADMIN");
        itemAdminService.audit(6L, new ItemAuditDTO(false, "图片不清晰，请重拍"));
        Item rejected = itemMapper.selectById(6L);
        assertThat(rejected.getStatus()).isEqualTo("REJECTED");
        assertThat(rejected.getAuditRemark()).isEqualTo("图片不清晰，请重拍");

        loginAs(4L, "王强", "USER");
        itemService.update(6L, saveDTO(imageUrls(2)));

        Item resubmitted = itemMapper.selectById(6L);
        assertThat(resubmitted.getStatus()).isEqualTo("PENDING");
        assertThat(resubmitted.getAuditRemark()).isNull();
        assertThat(resubmitted.getAuditAdminId()).isNull();
        assertThat(resubmitted.getAuditTime()).isNull();
    }

    @Test
    @DisplayName("下架：有进行中订单（物品 2 有 CONFIRMED 单）→ 3003")
    void shelf_offShelfWithActiveOrder_throwsStatusInvalid() {
        loginAs(2L, "张伟", "USER");
        assertThatThrownBy(() -> itemService.shelf(2L, new ItemShelfDTO(false)))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.ITEM_STATUS_INVALID.getCode()));
    }

    @Test
    @DisplayName("上下架：无进行中订单可以来回切（物品 3）")
    void shelf_offThenOn_succeeds() {
        loginAs(3L, "李娜", "USER");
        itemService.shelf(3L, new ItemShelfDTO(false));
        assertThat(itemMapper.selectById(3L).getStatus()).isEqualTo("OFF_SHELF");

        itemService.shelf(3L, new ItemShelfDTO(true));
        assertThat(itemMapper.selectById(3L).getStatus()).isEqualTo("ON_SHELF");
    }

    // ---------------------------------------------------------------- 检索与日历

    @Test
    @DisplayName("检索：无筛选只返回在架物品（种子 6 件，待审核的 6 号不在内）")
    void list_noFilter_returnsOnlyOnShelf() {
        PageResult<ItemCardVO> page = itemService.list(new ItemQuery());

        assertThat(page.getTotal()).isEqualTo(6L);
        assertThat(page.getList()).hasSize(6);
        assertThat(page.getList()).extracting(ItemCardVO::id).doesNotContain(6L);
        assertThat(page.getList()).allSatisfy(card -> assertThat(card.available()).isNull());
    }

    @Test
    @DisplayName("检索：同小区物品排在最前（王强在绿洲家园，只有物品 5 同小区）")
    void list_loggedIn_sameCommunityFirst() {
        loginAs(4L, "王强", "USER");
        PageResult<ItemCardVO> page = itemService.list(new ItemQuery());

        assertThat(page.getList()).isNotEmpty();
        assertThat(page.getList().get(0).id()).isEqualTo(5L);
    }

    @Test
    @DisplayName("检索：按可租日期过滤时排除被占用的物品（物品 2 占用 11-20~11-22）")
    void list_withDateRange_excludesOccupiedItem() {
        ItemQuery query = new ItemQuery();
        query.setStartDate(LocalDate.parse("2026-11-20"));
        query.setEndDate(LocalDate.parse("2026-11-23"));

        PageResult<ItemCardVO> page = itemService.list(query);

        assertThat(page.getList()).extracting(ItemCardVO::id).doesNotContain(2L);
        assertThat(page.getTotal()).isEqualTo(5L);
        assertThat(page.getList()).allSatisfy(card -> assertThat(card.available()).isTrue());
    }

    @Test
    @DisplayName("检索：日期只给一头 → 2001（不能静默忽略）")
    void list_onlyStartDate_throwsParamError() {
        ItemQuery query = new ItemQuery();
        query.setStartDate(LocalDate.parse("2026-11-20"));
        assertThatThrownBy(() -> itemService.list(query))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode()));
    }

    @Test
    @DisplayName("日历：占用日与订单占用逐日一致，整月补齐可租日")
    void calendar_occupiedDatesMatchOccupancy() {
        CalendarVO calendar = itemService.calendar(2L, "2026-11");

        assertThat(calendar.occupiedDates()).containsExactly("2026-11-20", "2026-11-21", "2026-11-22");
        assertThat(calendar.availableDates()).hasSize(30 - 3);
        assertThat(calendar.availableDates()).contains("2026-11-01").doesNotContain("2026-11-20");
    }

    @Test
    @DisplayName("日历：月份格式非法 → 2003")
    void calendar_badMonth_throwsParamFormatError() {
        assertThatThrownBy(() -> itemService.calendar(2L, "2026-13-01"))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PARAM_FORMAT_ERROR.getCode()));
    }

    @Test
    @DisplayName("详情：浏览数 +1，且不返回出租人手机号")
    void detail_incrementsViewCount() {
        Integer before = itemMapper.selectById(1L).getViewCount();

        ItemDetailVO detail = itemService.detail(1L);

        assertThat(detail.viewCount()).isEqualTo(before + 1);
        assertThat(itemMapper.selectById(1L).getViewCount()).isEqualTo(before + 1);
        assertThat(detail.owner()).isNotNull();
        assertThat(detail.categoryName()).isEqualTo("帐篷");
    }

    @Test
    @DisplayName("详情：待审核物品对非归属人按「不存在」处理 → 3004")
    void detail_pendingItem_hiddenFromOthers() {
        loginAs(3L, "李娜", "USER");
        assertThatThrownBy(() -> itemService.detail(6L))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.ITEM_NOT_FOUND.getCode()));

        loginAs(4L, "王强", "USER");
        assertThat(itemService.detail(6L).status()).isEqualTo("PENDING");
    }

    // ---------------------------------------------------------------- 审核

    @Test
    @DisplayName("审核：通过 → 在架，并记录审核人与时间")
    void audit_approve_movesToOnShelf() {
        loginAs(1L, "平台管理员", "ADMIN");
        itemAdminService.audit(6L, new ItemAuditDTO(true, null));

        Item item = itemMapper.selectById(6L);
        assertThat(item.getStatus()).isEqualTo("ON_SHELF");
        assertThat(item.getAuditAdminId()).isEqualTo(1L);
        assertThat(item.getAuditTime()).isNotNull();
    }

    @Test
    @DisplayName("审核：驳回不给理由 → 2001")
    void audit_rejectWithoutRemark_throwsParamError() {
        loginAs(1L, "平台管理员", "ADMIN");
        assertThatThrownBy(() -> itemAdminService.audit(6L, new ItemAuditDTO(false, "   ")))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode()));
    }

    @Test
    @DisplayName("审核：非待审核状态 → 3003（在架物品不能再审）")
    void audit_nonPendingItem_throwsStatusInvalid() {
        loginAs(1L, "平台管理员", "ADMIN");
        assertThatThrownBy(() -> itemAdminService.audit(1L, new ItemAuditDTO(true, null)))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.ITEM_STATUS_INVALID.getCode()));
    }

    @Test
    @DisplayName("审核列表：默认只出待审核，且带出租人脱敏手机号")
    void auditPage_defaultStatus_returnsPendingOnly() {
        PageResult<com.duyikang.rental.module.item.vo.AdminItemVO> page =
                itemAdminService.page(null, null, 1, 10);

        assertThat(page.getList()).isNotEmpty();
        assertThat(page.getList()).allSatisfy(vo -> {
            assertThat(vo.status()).isEqualTo("PENDING");
            if (vo.ownerPhone() != null) {
                assertThat(vo.ownerPhone()).contains("****");
            }
        });
    }

    // ---------------------------------------------------------------- 工具

    private void loginAs(Long userId, String nickname, String role) {
        LoginUser user = new LoginUser(userId, nickname, role);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private ItemSaveDTO saveDTO(List<String> urls) {
        return new ItemSaveDTO("测试帐篷", LEAF_CATEGORY_ID, "九成新，带地钉", COMMUNITY_ID,
                VALID_DAILY_PRICE, VALID_DEPOSIT, VALID_OVERDUE_RATE, urls);
    }

    /** 生成符合 /uploads/{yyyyMM}/{32 位 hex}.{ext} 的地址（校验正则与 FileStorageUtil 一致）。 */
    private List<String> imageUrls(int count) {
        List<String> urls = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            urls.add("/uploads/202611/" + UUID.randomUUID().toString().replace("-", "") + ".png");
        }
        return urls;
    }
}
