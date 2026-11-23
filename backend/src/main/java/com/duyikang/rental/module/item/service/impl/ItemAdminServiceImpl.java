package com.duyikang.rental.module.item.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.duyikang.rental.common.constant.ErrorCode;
import com.duyikang.rental.common.constant.ItemStatus;
import com.duyikang.rental.common.constant.MessageType;
import com.duyikang.rental.common.exception.BizException;
import com.duyikang.rental.common.result.PageResult;
import com.duyikang.rental.module.item.convert.ItemConvert;
import com.duyikang.rental.module.item.dto.ItemAuditDTO;
import com.duyikang.rental.module.item.entity.Category;
import com.duyikang.rental.module.item.entity.Community;
import com.duyikang.rental.module.item.entity.Item;
import com.duyikang.rental.module.item.entity.ItemImage;
import com.duyikang.rental.module.item.mapper.CategoryMapper;
import com.duyikang.rental.module.item.mapper.CommunityMapper;
import com.duyikang.rental.module.item.mapper.ItemImageMapper;
import com.duyikang.rental.module.item.mapper.ItemMapper;
import com.duyikang.rental.module.message.service.MessageService;
import com.duyikang.rental.module.item.service.ItemAdminService;
import com.duyikang.rental.module.item.vo.AdminItemVO;
import com.duyikang.rental.module.item.vo.ItemImageVO;
import com.duyikang.rental.module.user.convert.UserConvert;
import com.duyikang.rental.module.user.entity.User;
import com.duyikang.rental.module.user.mapper.UserMapper;
import com.duyikang.rental.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 物品审核实现。
 *
 * <p>审核动作是状态机的入口：待审核 {@code PENDING} 只能被审核一次，
 * 通过后在架、驳回后进"驳回"（租用人不能编辑直接重提，必须先改再提 → 见 ItemServiceImpl#update）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ItemAdminServiceImpl implements ItemAdminService {

    private static final Set<String> ALL_STATUSES = Set.of(
            ItemStatus.PENDING, ItemStatus.ON_SHELF, ItemStatus.OFF_SHELF,
            ItemStatus.RENTED, ItemStatus.MAINTAINING, ItemStatus.REJECTED);

    /** 审核列表默认只看待审核：管理端最常用的是"待办"，不是"全部"。 */
    private static final String DEFAULT_STATUS = ItemStatus.PENDING;

    private static final int PAGE_SIZE_DEFAULT = 10;
    private static final int PAGE_SIZE_MAX = 100;
    private static final int REMARK_MAX = 255;

    private final ItemMapper itemMapper;
    private final ItemImageMapper itemImageMapper;
    private final CategoryMapper categoryMapper;
    private final CommunityMapper communityMapper;
    private final UserMapper userMapper;
    private final MessageService messageService;

    @Override
    public PageResult<AdminItemVO> page(String status, String keyword, Integer pageNum, Integer pageSize) {
        String wanted = (status == null || status.isBlank()) ? DEFAULT_STATUS : status;
        if (!ALL_STATUSES.contains(wanted)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "状态取值不合法：" + status);
        }
        long num = pageNum == null || pageNum < 1 ? 1L : pageNum;
        long size = pageSize == null || pageSize < 1 ? PAGE_SIZE_DEFAULT : Math.min(pageSize, PAGE_SIZE_MAX);

        Page<Item> page = itemMapper.selectPage(new Page<>(num, size),
                Wrappers.<Item>lambdaQuery()
                        .eq(Item::getStatus, wanted)
                        .like(keyword != null && !keyword.isBlank(), Item::getTitle, keyword == null ? "" : keyword.trim())
                        .orderByAsc(Item::getCreatedAt)
                        .orderByAsc(Item::getId));

        List<Item> records = page.getRecords();
        if (records.isEmpty()) {
            return PageResult.of(List.of(), page.getTotal(), num, size);
        }

        Map<Long, String> categoryNames = nameMap(
                records.stream().map(Item::getCategoryId).toList(), Category::getId, Category::getName,
                ids -> categoryMapper.selectBatchIds(ids));
        Map<Long, String> communityNames = nameMap(
                records.stream().map(Item::getCommunityId).toList(), Community::getId, Community::getName,
                ids -> communityMapper.selectBatchIds(ids));

        List<Long> itemIds = records.stream().map(Item::getId).toList();
        Map<Long, List<ItemImageVO>> imagesByItem = imageMap(itemIds);

        List<Long> ownerIds = records.stream().map(Item::getOwnerId).filter(Objects::nonNull).distinct().toList();
        Map<Long, User> owners = ownerIds.isEmpty()
                ? Map.of()
                : userMapper.selectBatchIds(ownerIds).stream().collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        List<AdminItemVO> list = new ArrayList<>(records.size());
        for (Item item : records) {
            User owner = owners.get(item.getOwnerId());
            list.add(ItemConvert.toAdminItem(
                    item,
                    categoryNames.get(item.getCategoryId()),
                    communityNames.get(item.getCommunityId()),
                    owner == null ? item.getOwnerId() : owner.getId(),
                    owner == null ? null : owner.getNickname(),
                    // 管理端需要联系出租人来核实实物，故给脱敏手机号；完整号码不在任何列表接口里出现
                    owner == null ? null : UserConvert.maskPhone(owner.getPhone()),
                    imagesByItem.getOrDefault(item.getId(), List.of())));
        }
        return PageResult.of(list, page.getTotal(), num, size);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long itemId, ItemAuditDTO dto) {
        Item item = itemMapper.selectById(itemId);
        if (item == null) {
            throw new BizException(ErrorCode.ITEM_NOT_FOUND);
        }
        if (!ItemStatus.PENDING.equals(item.getStatus())) {
            throw new BizException(ErrorCode.ITEM_STATUS_INVALID,
                    "该物品当前状态为「" + ItemStatus.labelOf(item.getStatus()) + "」，不支持审核");
        }

        boolean approved = Boolean.TRUE.equals(dto.approved());
        String remark = dto.remark() == null ? null : dto.remark().trim();
        if (remark != null && remark.length() > REMARK_MAX) {
            throw new BizException(ErrorCode.PARAM_ERROR, "审核意见不能超过 " + REMARK_MAX + " 字");
        }
        // 驳回必须给理由：出租人需要知道改什么
        if (!approved && (remark == null || remark.isEmpty())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "驳回时必须填写驳回理由");
        }

        String nextStatus = approved ? ItemStatus.ON_SHELF : ItemStatus.REJECTED;
        Long adminId = CurrentUser.requireId();

        itemMapper.update(null, Wrappers.<Item>lambdaUpdate()
                .eq(Item::getId, itemId)
                .eq(Item::getStatus, ItemStatus.PENDING)
                .set(Item::getStatus, nextStatus)
                .set(Item::getAuditRemark, remark)
                .set(Item::getAuditAdminId, adminId)
                .set(Item::getAuditTime, LocalDateTime.now()));

        messageService.notify(item.getOwnerId(), MessageType.SYSTEM,
                approved ? "物品审核通过" : "物品审核未通过",
                approved
                        ? "「" + item.getTitle() + "」已上架，可以在列表页看到它了。"
                        : "「" + item.getTitle() + "」未通过审核：" + remark + "。修改后可重新提交。",
                MessageType.BIZ_ITEM, itemId);

        log.info("物品审核完成 itemId={} approved={} nextStatus={} adminId={}", itemId, approved, nextStatus, adminId);
    }

    private Map<Long, List<ItemImageVO>> imageMap(Collection<Long> itemIds) {
        Map<Long, List<ItemImageVO>> result = new HashMap<>();
        if (itemIds.isEmpty()) {
            return result;
        }
        List<ItemImage> images = itemImageMapper.selectList(Wrappers.<ItemImage>lambdaQuery()
                .in(ItemImage::getItemId, itemIds)
                .orderByAsc(ItemImage::getId));
        for (ItemImage image : images) {
            result.computeIfAbsent(image.getItemId(), k -> new ArrayList<>())
                    .add(new ItemImageVO(image.getId(), image.getUrl()));
        }
        return result;
    }

    /** 批量取名：一次查一批，不做 N+1。 */
    private <T> Map<Long, String> nameMap(Collection<Long> rawIds,
                                          java.util.function.Function<T, Long> idGetter,
                                          java.util.function.Function<T, String> nameGetter,
                                          java.util.function.Function<List<Long>, List<T>> loader) {
        List<Long> ids = rawIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> map = new HashMap<>();
        for (T row : loader.apply(ids)) {
            map.put(idGetter.apply(row), nameGetter.apply(row));
        }
        return map;
    }
}
