package com.duyikang.rental.module.recommend.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.duyikang.rental.common.constant.ErrorCode;
import com.duyikang.rental.common.constant.ItemStatus;
import com.duyikang.rental.common.exception.BizException;
import com.duyikang.rental.module.item.mapper.CommunityMapper;
import com.duyikang.rental.module.item.entity.Community;
import com.duyikang.rental.module.item.entity.Category;
import com.duyikang.rental.module.item.entity.Item;
import com.duyikang.rental.module.item.mapper.CategoryMapper;
import com.duyikang.rental.module.item.mapper.ItemMapper;
import com.duyikang.rental.module.order.entity.RentalOrder;
import com.duyikang.rental.module.order.mapper.RentalOrderMapper;
import com.duyikang.rental.module.recommend.service.RecommendService;
import com.duyikang.rental.module.recommend.vo.RecommendItemVO;
import com.duyikang.rental.module.recommend.vo.RecommendResultVO;
import com.duyikang.rental.module.system.entity.SysConfig;
import com.duyikang.rental.module.system.mapper.SysConfigMapper;
import com.duyikang.rental.module.user.entity.User;
import com.duyikang.rental.module.user.mapper.UserMapper;
import com.duyikang.rental.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 混合推荐实现：协同过滤（CF）+ 内容 + 热度，权重全部读配置。
 *
 * <p><b>为什么不用矩阵分解/Embedding</b>：本课题的订单量是几十到几百条，最缺的是"可解释"和"可演示"，
 * 不是拟合能力。item-based 共现 + 类目相似 + 热度，三个分项各自能翻译成一句人话，
 * 答辩时每一条推荐都能说清"凭什么推它"，这正好对应 FR-M5-01 的可解释性要求。</p>
 *
 * <p><b>性能</b>：只发 4 条 SQL（候选物品、我的订单、全量共现边、字典），打分在内存完成，候选上限 200 条。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendServiceImpl implements RecommendService {

    private final ItemMapper itemMapper;
    private final RentalOrderMapper rentalOrderMapper;
    private final CategoryMapper categoryMapper;
    private final CommunityMapper communityMapper;
    private final UserMapper userMapper;
    private final SysConfigMapper sysConfigMapper;

    private static final int DEFAULT_LIMIT = 6;
    private static final int MAX_LIMIT = 20;
    private static final int CANDIDATE_LIMIT = 200;

    private static final String KEY_W_CF = "recommend.w.cf";
    private static final String KEY_W_CONTENT = "recommend.w.content";
    private static final String KEY_W_HEAT = "recommend.w.heat";
    private static final String KEY_HALF_LIFE = "recommend.decay.half_life";
    private static final String KEY_HEAT_WINDOW = "recommend.heat.window";

    /** 仅当 sys_config 缺键时兜底，正常路径一律读库（§9 配置外置）。 */
    private static final BigDecimal FALLBACK_W_CF = new BigDecimal("0.6");
    private static final BigDecimal FALLBACK_W_CONTENT = new BigDecimal("0.3");
    private static final BigDecimal FALLBACK_W_HEAT = new BigDecimal("0.1");
    private static final BigDecimal FALLBACK_HALF_LIFE = new BigDecimal("30");

    private static final List<String> ACTIVE_STATUSES =
            List.of("PENDING", "CONFIRMED", "IN_USE", "OVERDUE");

    private static final String REASON_CF = "CF";
    private static final String REASON_CONTENT = "CONTENT";
    private static final String REASON_HEAT = "HEAT";
    private static final String REASON_COLD_START = "COLD_START";

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = BigDecimal.ONE;

    @Override
    public RecommendResultVO recommend(Integer limit) {
        int size = normalizeLimit(limit);
        Map<String, String> config = loadConfig();

        BigDecimal wCf = decimalOf(config, KEY_W_CF, FALLBACK_W_CF);
        BigDecimal wContent = decimalOf(config, KEY_W_CONTENT, FALLBACK_W_CONTENT);
        BigDecimal wHeat = decimalOf(config, KEY_W_HEAT, FALLBACK_W_HEAT);
        BigDecimal halfLifeDays = decimalOf(config, KEY_HALF_LIFE, FALLBACK_HALF_LIFE);
        RecommendResultVO.WeightsVO weights = new RecommendResultVO.WeightsVO(wCf, wContent, wHeat);

        User me = CurrentUser.optional()
                .map(loginUser -> userMapper.selectById(loginUser.userId()))
                .orElse(null);

        List<Item> candidates = itemMapper.selectPage(new Page<>(1, CANDIDATE_LIMIT),
                Wrappers.<Item>lambdaQuery()
                        .eq(Item::getStatus, ItemStatus.ON_SHELF)
                        .orderByDesc(Item::getRentCount)
                        .orderByDesc(Item::getId)).getRecords();
        if (candidates.isEmpty()) {
            return new RecommendResultVO(size, me == null, weights, List.of());
        }

        Map<Long, Category> categories = categoryMapper.selectList(null).stream()
                .collect(Collectors.toMap(Category::getId, category -> category, (a, b) -> a));
        Map<Long, Community> communities = communityMapper.selectList(null).stream()
                .collect(Collectors.toMap(Community::getId, community -> community, (a, b) -> a));

        // 我的订单：既用来判断"正在租"（候选要排除），也用来算历史偏好
        List<RentalOrder> myOrders = me == null ? List.of() : rentalOrderMapper.selectList(
                Wrappers.<RentalOrder>lambdaQuery()
                        .eq(RentalOrder::getRenterId, me.getId())
                        .select(RentalOrder::getItemId, RentalOrder::getStatus, RentalOrder::getCreatedAt));

        Set<Long> activeItemIds = myOrders.stream()
                .filter(order -> ACTIVE_STATUSES.contains(order.getStatus()))
                .map(RentalOrder::getItemId)
                .collect(Collectors.toSet());

        Map<Long, LocalDateTime> lastRentAt = new HashMap<>();
        for (RentalOrder order : myOrders) {
            lastRentAt.merge(order.getItemId(), order.getCreatedAt(),
                    (a, b) -> a.isAfter(b) ? a : b);
        }
        boolean coldStart = lastRentAt.isEmpty();

        // 历史物品的类目与价格（内容相似度要用）
        List<Item> myItems = lastRentAt.isEmpty()
                ? List.of()
                : itemMapper.selectBatchIds(lastRentAt.keySet());
        Set<Long> mySecondaryCategoryIds = myItems.stream()
                .map(Item::getCategoryId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Set<Long> myPrimaryCategoryIds = mySecondaryCategoryIds.stream()
                .map(categories::get).filter(java.util.Objects::nonNull)
                .map(Category::getParentId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        BigDecimal myAvgPrice = averagePrice(myItems);

        // 共现边：itemId -> 租过它的用户集合
        Map<Long, Set<Long>> itemUsers = new HashMap<>();
        for (RentalOrder order : rentalOrderMapper.selectList(Wrappers.<RentalOrder>lambdaQuery()
                .select(RentalOrder::getRenterId, RentalOrder::getItemId))) {
            itemUsers.computeIfAbsent(order.getItemId(), key -> new HashSet<>()).add(order.getRenterId());
        }

        List<Scored> scored = new ArrayList<>(candidates.size());
        for (Item item : candidates) {
            if (me != null && me.getId().equals(item.getOwnerId())) {
                continue; // 不推荐自己发布的
            }
            if (activeItemIds.contains(item.getId())) {
                continue; // 正在租的不重复推
            }
            Category category = categories.get(item.getCategoryId());
            double cf = coldStart ? 0D : cf(item.getId(), lastRentAt, itemUsers, halfLifeDays);
            double content = coldStart
                    ? contentCold(me, item, communities)
                    : contentWarm(item, category, mySecondaryCategoryIds, myPrimaryCategoryIds, myAvgPrice);
            double heat = heatRaw(item);
            scored.add(new Scored(item, category, cf, content, heat, me, communities));
        }
        if (scored.isEmpty()) {
            return new RecommendResultVO(size, coldStart, weights, List.of());
        }

        double maxCf = scored.stream().mapToDouble(s -> s.cf).max().orElse(0D);
        double maxHeat = scored.stream().mapToDouble(s -> s.heat).max().orElse(0D);
        LocalDateTime latestRentAt = lastRentAt.values().stream().max(LocalDateTime::compareTo).orElse(null);
        String latestRentTitle = myItems.stream()
                .filter(item -> latestRentAt != null && lastRentAt.get(item.getId()) != null
                        && lastRentAt.get(item.getId()).equals(latestRentAt))
                .map(Item::getTitle).findFirst().orElse(myItems.isEmpty() ? "" : myItems.get(0).getTitle());
        String myCommunityName = me == null || me.getCommunityId() == null
                ? null
                : java.util.Optional.ofNullable(communities.get(me.getCommunityId()))
                        .map(Community::getName).orElse(null);

        for (Scored s : scored) {
            s.cf = maxCf > 0 ? s.cf / maxCf : 0D;
            s.heat = maxHeat > 0 ? s.heat / maxHeat : 0D;
            s.score = wCf.doubleValue() * s.cf
                    + wContent.doubleValue() * s.content
                    + wHeat.doubleValue() * s.heat;
        }

        scored.sort(Comparator
                .comparingDouble((Scored s) -> s.score).reversed()
                .thenComparing(s -> s.item.getRentCount() == null ? 0 : s.item.getRentCount(), Comparator.reverseOrder())
                .thenComparing(s -> s.item.getId(), Comparator.reverseOrder()));

        List<RecommendItemVO> list = new ArrayList<>(size);
        for (Scored s : scored) {
            if (list.size() >= size) {
                break;
            }
            String reasonCode = coldStart
                    ? REASON_COLD_START
                    : dominantReason(wCf.doubleValue() * s.cf,
                            wContent.doubleValue() * s.content,
                            wHeat.doubleValue() * s.heat);
            list.add(toVO(s, reasonCode, reasonText(reasonCode, s, latestRentTitle, myCommunityName, me != null)));
        }
        log.debug("推荐完成：userId={}，候选={}，返回={}，coldStart={}", me == null ? null : me.getId(),
                scored.size(), list.size(), coldStart);
        return new RecommendResultVO(size, coldStart, weights, list);
    }

    /** item-based 共现 + 时间衰减：越久以前租过的东西，对今天推荐的拉动越小。 */
    private double cf(Long candidateId, Map<Long, LocalDateTime> lastRentAt,
                      Map<Long, Set<Long>> itemUsers, BigDecimal halfLifeDays) {
        Set<Long> candidateUsers = itemUsers.get(candidateId);
        if (candidateUsers == null || candidateUsers.isEmpty()) {
            return 0D;
        }
        double halfLifeHours = Math.max(1D, halfLifeDays.doubleValue() * 24D);
        double total = 0D;
        for (Map.Entry<Long, LocalDateTime> entry : lastRentAt.entrySet()) {
            if (entry.getKey().equals(candidateId)) {
                continue;
            }
            Set<Long> otherUsers = itemUsers.get(entry.getKey());
            if (otherUsers == null || otherUsers.isEmpty()) {
                continue;
            }
            long overlap = candidateUsers.stream().filter(otherUsers::contains).count();
            if (overlap <= 0) {
                continue;
            }
            double hours = Math.max(0D, Duration.between(entry.getValue(), LocalDateTime.now()).toHours());
            double decay = Math.pow(0.5D, hours / halfLifeHours);
            total += overlap * decay;
        }
        return total;
    }

    private double contentWarm(Item item, Category category, Set<Long> mySecondary, Set<Long> myPrimary,
                               BigDecimal myAvgPrice) {
        double score = 0D;
        if (category != null && mySecondary.contains(category.getId())) {
            score += 0.5D;
        }
        if (category != null && category.getParentId() != null && myPrimary.contains(category.getParentId())) {
            score += 0.3D;
        }
        if (myAvgPrice != null && myAvgPrice.signum() > 0 && item.getDailyPrice() != null
                && item.getDailyPrice().signum() > 0) {
            BigDecimal diff = item.getDailyPrice().subtract(myAvgPrice).abs();
            BigDecimal max = item.getDailyPrice().max(myAvgPrice);
            if (max.signum() > 0) {
                BigDecimal sim = ONE.subtract(diff.divide(max, 4, RoundingMode.HALF_UP));
                score += 0.2D * Math.max(0D, sim.doubleValue());
            }
        }
        return Math.min(1D, Math.max(0D, score));
    }

    /** 冷启动：同小区 → 同片区 → 其他；匿名时全部为 0，只靠热度排序。 */
    private double contentCold(User me, Item item, Map<Long, Community> communities) {
        if (me == null || me.getCommunityId() == null || item.getCommunityId() == null) {
            return 0D;
        }
        if (me.getCommunityId().equals(item.getCommunityId())) {
            return 1D;
        }
        Community mine = communities.get(me.getCommunityId());
        Community theirs = communities.get(item.getCommunityId());
        if (mine != null && theirs != null && mine.getDistrict() != null
                && mine.getDistrict().equals(theirs.getDistrict())) {
            return 0.6D;
        }
        return 0D;
    }

    private double heatRaw(Item item) {
        int rentCount = item.getRentCount() == null ? 0 : item.getRentCount();
        int viewCount = item.getViewCount() == null ? 0 : item.getViewCount();
        return rentCount + 0.2D * viewCount;
    }

    private String dominantReason(double cfPart, double contentPart, double heatPart) {
        if (cfPart >= contentPart && cfPart >= heatPart) {
            return REASON_CF;
        }
        return contentPart >= heatPart ? REASON_CONTENT : REASON_HEAT;
    }

    private String reasonText(String reasonCode, Scored s, String latestRentTitle,
                              String myCommunityName, boolean loggedIn) {
        return switch (reasonCode) {
            case REASON_CF -> "你租过的「" + latestRentTitle + "」常被同一批人一起租";
            case REASON_CONTENT -> "同类目「" + s.categoryName() + "」· 价格和你常租的接近";
            case REASON_HEAT -> "最近 30 天被租了 " + (s.item.getRentCount() == null ? 0 : s.item.getRentCount()) + " 次";
            default -> loggedIn && myCommunityName != null
                    ? "你小区「" + myCommunityName + "」的新上架 / 热门物品"
                    : "近期热门物品（按租出次数排序）";
        };
    }

    private RecommendItemVO toVO(Scored s, String reasonCode, String reason) {
        String communityName = s.communities.get(s.item.getCommunityId()) == null
                ? null : s.communities.get(s.item.getCommunityId()).getName();
        return new RecommendItemVO(
                s.item.getId(),
                s.item.getTitle(),
                s.item.getCoverUrl(),
                s.item.getCategoryId(),
                s.categoryName(),
                s.item.getCommunityId(),
                communityName,
                s.item.getDailyPrice(),
                s.item.getDeposit(),
                s.item.getRentCount(),
                BigDecimal.valueOf(s.score).setScale(4, RoundingMode.HALF_UP),
                reasonCode,
                reason);
    }

    private BigDecimal averagePrice(List<Item> items) {
        List<BigDecimal> prices = items.stream().map(Item::getDailyPrice)
                .filter(java.util.Objects::nonNull).toList();
        if (prices.isEmpty()) {
            return ZERO;
        }
        BigDecimal sum = prices.stream().reduce(ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(prices.size()), 4, RoundingMode.HALF_UP);
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new BizException(ErrorCode.PARAM_ERROR, "limit 应在 1~" + MAX_LIMIT + " 之间");
        }
        return limit;
    }

    private Map<String, String> loadConfig() {
        Map<String, String> map = new HashMap<>();
        for (SysConfig config : sysConfigMapper.selectList(Wrappers.<SysConfig>lambdaQuery()
                .likeRight(SysConfig::getConfigKey, "recommend."))) {
            map.put(config.getConfigKey(), config.getConfigValue());
        }
        return map;
    }

    private BigDecimal decimalOf(Map<String, String> config, String key, BigDecimal fallback) {
        String raw = config.get(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            log.warn("配置 {} 不是合法数字（{}），本次回退默认值 {}", key, raw, fallback);
            return fallback;
        }
    }

    /** 打分的中间态（可变，便于归一化时原地覆盖）。 */
    private static final class Scored {
        private final Item item;
        private final Category category;
        private final Map<Long, Community> communities;
        private double cf;
        private final double content;
        private double heat;
        private double score;

        private Scored(Item item, Category category, double cf, double content, double heat,
                       User me, Map<Long, Community> communities) {
            this.item = item;
            this.category = category;
            this.cf = cf;
            this.content = content;
            this.heat = heat;
            this.communities = communities;
        }

        private String categoryName() {
            return category == null ? "" : category.getName();
        }
    }
}
