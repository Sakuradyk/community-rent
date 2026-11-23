package com.duyikang.rental.module.item.convert;

import com.duyikang.rental.common.constant.ItemStatus;
import com.duyikang.rental.module.item.entity.Item;
import com.duyikang.rental.module.item.vo.AdminItemVO;
import com.duyikang.rental.module.item.vo.ItemCardVO;
import com.duyikang.rental.module.item.vo.ItemDetailVO;
import com.duyikang.rental.module.item.vo.ItemImageVO;
import com.duyikang.rental.module.item.vo.ItemOwnerVO;
import com.duyikang.rental.module.item.vo.MyItemVO;

import java.util.List;

/**
 * 物品实体 → 视图对象的手写转换。
 *
 * <p>名字（类目名/社区名）由 Service 批量查好再传进来：转换类只做形状映射，不查库，
 * 这样它可以在单元测试里被直接调用，也不会让每个 VO 都暗含一次 SQL。</p>
 */
public final class ItemConvert {

    private ItemConvert() {
    }

    /** 列表卡片。{@code available} 传 null 表示"本次没问可租性"。 */
    public static ItemCardVO toCard(Item item, String categoryName, String communityName, Boolean available) {
        return new ItemCardVO(
                item.getId(),
                item.getTitle(),
                item.getCoverUrl(),
                item.getDailyPrice(),
                item.getDeposit(),
                item.getCategoryId(),
                categoryName,
                item.getCommunityId(),
                communityName,
                item.getRentCount(),
                available
        );
    }

    /** 我的物品。 */
    public static MyItemVO toMyItem(Item item, String categoryName, String communityName, int imageCount) {
        return new MyItemVO(
                item.getId(),
                item.getTitle(),
                item.getCoverUrl(),
                item.getDailyPrice(),
                item.getDeposit(),
                item.getCategoryId(),
                categoryName,
                item.getCommunityId(),
                communityName,
                item.getRentCount(),
                item.getStatus(),
                ItemStatus.labelOf(item.getStatus()),
                item.getAuditRemark(),
                imageCount,
                item.getLossValue(),
                item.getCreatedAt()
        );
    }

    /** 详情。 */
    public static ItemDetailVO toDetail(Item item,
                                       String categoryName,
                                       String communityName,
                                       String maintenanceState,
                                       List<ItemImageVO> images,
                                       ItemOwnerVO owner) {
        return new ItemDetailVO(
                item.getId(),
                item.getTitle(),
                item.getDescription(),
                item.getCategoryId(),
                categoryName,
                item.getCommunityId(),
                communityName,
                item.getDailyPrice(),
                item.getDeposit(),
                item.getOverdueRate(),
                item.getLossValue(),
                maintenanceState,
                item.getStatus(),
                images,
                owner,
                item.getRentCount(),
                item.getViewCount(),
                item.getCreatedAt()
        );
    }

    /** 管理端列表项。{@code ownerPhone} 由调用方传入**已脱敏**的号码。 */
    public static AdminItemVO toAdminItem(Item item,
                                        String categoryName,
                                        String communityName,
                                        Long ownerId,
                                        String ownerNickname,
                                        String maskedPhone,
                                        List<ItemImageVO> images) {
        return new AdminItemVO(
                item.getId(),
                item.getTitle(),
                item.getDescription(),
                item.getCoverUrl(),
                item.getDailyPrice(),
                item.getDeposit(),
                item.getOverdueRate(),
                item.getCategoryId(),
                categoryName,
                item.getCommunityId(),
                communityName,
                item.getStatus(),
                ItemStatus.labelOf(item.getStatus()),
                item.getAuditRemark(),
                ownerId,
                ownerNickname,
                maskedPhone,
                images,
                item.getCreatedAt()
        );
    }
}
