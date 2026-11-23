package com.duyikang.rental.module.recommend.eval;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.duyikang.rental.module.item.entity.Category;
import com.duyikang.rental.module.item.entity.Community;
import com.duyikang.rental.module.item.entity.Item;
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
import com.duyikang.rental.security.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 推荐离线评估（M5 的定量证据，论文 §推荐评估 引用本类跑出的表）。
 *
 * <p><b>为什么要单独一个类</b>：它要造几百条虚拟行为、按时间切分、再跑三组权重做消融，
 * 属于"研究报告"而不是"回归测试"，所以不叫 {@code *Test}（不参与 {@code mvn test} 的默认扫描），
 * 需要手动执行：</p>
 *
 * <pre>
 * cd backend
 * mvn -Dtest=RecommendEvaluation -DfailIfNoSpecifiedTests=false test
 * </pre>
 *
 * <p><b>三个关键设计</b>：</p>
 * <ol>
 *   <li>复用生产代码 {@code RecommendService.recommend(limit, cutoff)}，不另写一套打分 ——
 *       论文里的数字必须是线上这套逻辑跑出来的，否则测的是"另一个算法"。</li>
 *   <li>时间切分：前 80% 的时间窗当历史，后 20% 当答案；{@code cutoff} 之后的订单
 *       既不能进用户画像，也不能进共现矩阵（否则等于拿答案喂模型）。</li>
 *   <li>全程 {@code @Transactional}：几百条虚拟用户/物品/订单在测试结束后自动回滚，
 *       <b>不污染开发库</b>。</li>
 * </ol>
 */
@SpringBootTest
@Transactional
class RecommendEvaluation {

    private static final Logger log = LoggerFactory.getLogger(RecommendEvaluation.class);

    /** 固定随机种子：同样的数据每次都跑出同样的数字，论文里的表才可复现。 */
    private static final long SEED = 20261123L;

    private static final int SYNTHETIC_USERS = 120;
    private static final int SYNTHETIC_ITEMS = 60;
    private static final int MIN_ORDERS_PER_USER = 4;
    private static final int MAX_ORDERS_PER_USER = 12;
    private static final int HISTORY_DAYS = 120;
    private static final int TEST_WINDOW_DAYS = 24;
    private static final int TOP_N = 10;
    private static final BigDecimal DAILY_PRICE = new BigDecimal("20.00");
    private static final BigDecimal DEPOSIT = new BigDecimal("100.00");
    private static final BigDecimal OVERDUE_RATE = new BigDecimal("0.5000");

    @Autowired
    private RecommendService recommendService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private ItemMapper itemMapper;
    @Autowired
    private RentalOrderMapper rentalOrderMapper;
    @Autowired
    private SysConfigMapper sysConfigMapper;
    @Autowired
    private com.duyikang.rental.module.item.mapper.CategoryMapper categoryMapper;
    @Autowired
    private com.duyikang.rental.module.item.mapper.CommunityMapper communityMapper;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("离线评估：三组权重下的 HitRate@10 / Precision@10 / Recall@10")
    void evaluate() throws IOException {
        Random random = new Random(SEED);

        LocalDateTime now = LocalDateTime.now().withNano(0);
        LocalDateTime windowStart = now.minusDays(HISTORY_DAYS);
        LocalDateTime cutoff = now.minusDays(TEST_WINDOW_DAYS);

        // ---------- 1. 真实库的数据量（说明"为什么必须造数据"） ----------
        long realUsers = userMapper.selectCount(Wrappers.<User>lambdaQuery().eq(User::getDeleted, 0));
        long realOrders = rentalOrderMapper.selectCount(null);

        // ---------- 2. 造虚拟物品（复用真实二级类目，内容相似度才有意义） ----------
        List<Category> leaves = categoryMapper.selectList(Wrappers.<Category>lambdaQuery()
                .eq(Category::getLevel, 2)
                .eq(Category::getStatus, "NORMAL")
                .orderByAsc(Category::getId));
        if (leaves.size() < 4) {
            throw new IllegalStateException("二级类目不足 4 个，无法构造评估数据");
        }
        List<Category> pool = leaves.subList(0, Math.min(8, leaves.size()));
        Map<Long, Long> leafToParent = pool.stream()
                .collect(Collectors.toMap(Category::getId, Category::getParentId, (a, b) -> a));

        List<Long> communityIds = communityMapper.selectList(Wrappers.<Community>lambdaQuery()
                        .eq(Community::getEnabled, 1)
                        .orderByAsc(Community::getId))
                .stream().map(Community::getId).toList();
        if (communityIds.isEmpty()) {
            throw new IllegalStateException("没有启用的社区，无法构造评估数据");
        }

        // 虚拟物品的 owner 用真实种子用户 2（张伟），承租人是虚拟用户，天然满足 owner <> renter
        Long ownerId = 2L;
        List<Item> items = new ArrayList<>(SYNTHETIC_ITEMS);
        Map<Long, Long> itemCategory = new HashMap<>();
        for (int i = 0; i < SYNTHETIC_ITEMS; i++) {
            Category leaf = pool.get(i % pool.size());
            Item item = new Item();
            item.setOwnerId(ownerId);
            item.setCategoryId(leaf.getId());
            item.setTitle("评估物品" + (i + 1));
            item.setDescription("离线评估用合成数据，测试结束自动回滚");
            item.setCoverUrl("/uploads/202611/00000000000000000000000000000000.png");
            item.setCommunityId(communityIds.get(i % communityIds.size()));
            item.setDailyPrice(DAILY_PRICE);
            item.setDeposit(DEPOSIT);
            item.setOverdueRate(OVERDUE_RATE);
            item.setLossValue(new BigDecimal("0.0000"));
            item.setStatus("ON_SHELF");
            // 热度信号：让"纯热度"这组基线有东西可排
            item.setRentCount(random.nextInt(40));
            item.setViewCount(random.nextInt(400));
            itemMapper.insert(item);
            items.add(item);
            itemCategory.put(item.getId(), leaf.getId());
        }

        // ---------- 3. 造虚拟用户 + 行为（带类目偏好，才有"可被推荐命中"的信号） ----------
        Map<Long, List<RentalOrder>> ordersByUser = new LinkedHashMap<>();
        List<Long> syntheticUserIds = new ArrayList<>(SYNTHETIC_USERS);
        int orderSeq = 0;
        for (int u = 0; u < SYNTHETIC_USERS; u++) {
            User user = new User();
            // 手机号 199 号段，与种子/真实用户（138/139/151）不冲突
            user.setPhone(String.format("199%08d", u + 1));
            user.setNickname("评估用户" + (u + 1));
            user.setPasswordHash("$2a$10$0000000000000000000000000000000000000000000000000000");
            user.setCommunityId(communityIds.get(u % communityIds.size()));
            user.setCreditScore(80);
            user.setStatus("NORMAL");
            user.setCreatedAt(windowStart);
            user.setUpdateTime(windowStart);
            user.setDeleted(0);
            userMapper.insert(user);
            syntheticUserIds.add(user.getId());

            // 偏好：主类目占 70%，同父类目 20%，其余 10% 随机
            Category favorite = pool.get(random.nextInt(pool.size()));
            Long favoriteParent = leafToParent.get(favorite.getId());
            List<Category> sameParent = pool.stream()
                    .filter(c -> !c.getId().equals(favorite.getId()) && c.getParentId().equals(favoriteParent))
                    .toList();

            int orderCount = MIN_ORDERS_PER_USER + random.nextInt(MAX_ORDERS_PER_USER - MIN_ORDERS_PER_USER + 1);
            List<RentalOrder> mine = new ArrayList<>(orderCount);
            Set<Long> usedItems = new HashSet<>();
            for (int o = 0; o < orderCount; o++) {
                Category target;
                double dice = random.nextDouble();
                if (dice < 0.70D) {
                    target = favorite;
                } else if (dice < 0.90D && !sameParent.isEmpty()) {
                    target = sameParent.get(random.nextInt(sameParent.size()));
                } else {
                    target = pool.get(random.nextInt(pool.size()));
                }
                List<Item> matched = items.stream()
                        .filter(item -> item.getCategoryId().equals(target.getId()))
                        .filter(item -> !usedItems.contains(item.getId()))
                        .toList();
                if (matched.isEmpty()) {
                    continue;
                }
                Item item = matched.get(random.nextInt(matched.size()));
                usedItems.add(item.getId());

                LocalDate start = windowStart.toLocalDate()
                        .plusDays(random.nextInt(HISTORY_DAYS - 3));
                int rentDays = 1 + random.nextInt(3);
                RentalOrder order = new RentalOrder();
                order.setOrderNo("E" + (++orderSeq));
                order.setItemId(item.getId());
                order.setOwnerId(ownerId);
                order.setRenterId(user.getId());
                order.setStartDate(start);
                order.setEndDate(start.plusDays(rentDays));
                order.setRentDays(rentDays);
                order.setStatus("COMPLETED");
                order.setDailyPrice(DAILY_PRICE);
                order.setDeposit(DEPOSIT);
                order.setOverdueRate(OVERDUE_RATE);
                order.setRentAmount(DAILY_PRICE.multiply(BigDecimal.valueOf(rentDays)));
                order.setOverdueDays(0);
                // 行为时间：租赁开始日的前一天下单（合理的因果顺序）
                order.setCreatedAt(start.minusDays(1).atTime(9 + random.nextInt(9), random.nextInt(60)));
                order.setUpdateTime(order.getCreatedAt());
                rentalOrderMapper.insert(order);
                mine.add(order);
            }
            ordersByUser.put(user.getId(), mine);
        }

        // ---------- 4. 三组权重（消融对照）+ 生产默认 ----------
        Map<String, BigDecimal[]> configs = new LinkedHashMap<>();
        configs.put("混合（本文默认 0.6 / 0.3 / 0.1）", new BigDecimal[]{
                new BigDecimal("0.6"), new BigDecimal("0.3"), new BigDecimal("0.1")});
        configs.put("纯协同过滤（1.0 / 0 / 0）", new BigDecimal[]{
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO});
        configs.put("纯内容相似（0 / 1.0 / 0）", new BigDecimal[]{
                BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ZERO});
        configs.put("纯热度（0 / 0 / 1.0）", new BigDecimal[]{
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE});

        List<Row> rows = new ArrayList<>();
        for (Map.Entry<String, BigDecimal[]> config : configs.entrySet()) {
            applyWeights(config.getValue()[0], config.getValue()[1], config.getValue()[2]);
            rows.add(evaluateOnce(config.getKey(), syntheticUserIds, ordersByUser, cutoff, now));
        }

        writeReport(rows, realUsers, realOrders, syntheticUserIds.size(), orderSeq, cutoff);
    }

    /** 用一组权重跑一遍，返回聚合指标。 */
    private Row evaluateOnce(String label,
                             List<Long> userIds,
                             Map<Long, List<RentalOrder>> ordersByUser,
                             LocalDateTime cutoff,
                             LocalDateTime now) {
        int evaluatedUsers = 0;
        int usersWithHit = 0;
        int totalHits = 0;
        int totalTestItems = 0;
        int coldStartUsers = 0;

        for (Long userId : userIds) {
            List<RentalOrder> orders = ordersByUser.get(userId);
            Set<Long> testItems = orders.stream()
                    .filter(order -> order.getCreatedAt().isAfter(cutoff))
                    .map(RentalOrder::getItemId)
                    .collect(Collectors.toSet());
            if (testItems.isEmpty()) {
                continue; // 评测期没有行为，既不可命中也不该进分母
            }
            evaluatedUsers++;
            totalTestItems += testItems.size();

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                            new LoginUser(userId, "eval-" + userId, "USER"), null,
                            List.of(new SimpleGrantedAuthority("ROLE_USER"))));
            RecommendResultVO result = recommendService.recommend(TOP_N, cutoff);
            if (result.coldStart()) {
                coldStartUsers++;
            }
            Set<Long> topIds = result.list().stream()
                    .map(RecommendItemVO::id)
                    .collect(Collectors.toSet());
            int hits = (int) testItems.stream().filter(topIds::contains).count();
            if (hits > 0) {
                usersWithHit++;
            }
            totalHits += hits;
        }
        SecurityContextHolder.clearContext();

        double hitRate = evaluatedUsers == 0 ? 0D : (double) usersWithHit / evaluatedUsers;
        double precision = evaluatedUsers == 0 ? 0D : (double) totalHits / (evaluatedUsers * TOP_N);
        double recall = totalTestItems == 0 ? 0D : (double) totalHits / totalTestItems;
        return new Row(label, evaluatedUsers, coldStartUsers, usersWithHit, totalHits, totalTestItems,
                hitRate, precision, recall);
    }

    private void applyWeights(BigDecimal cf, BigDecimal content, BigDecimal heat) {
        updateConfig("recommend.w.cf", cf.toPlainString());
        updateConfig("recommend.w.content", content.toPlainString());
        updateConfig("recommend.w.heat", heat.toPlainString());
    }

    private void updateConfig(String key, String value) {
        sysConfigMapper.update(null, Wrappers.<SysConfig>lambdaUpdate()
                .set(SysConfig::getConfigValue, value)
                .eq(SysConfig::getConfigKey, key));
    }

    private void writeReport(List<Row> rows,
                             long realUsers,
                             long realOrders,
                             int syntheticUsers,
                             int syntheticOrders,
                             LocalDateTime cutoff) throws IOException {
        StringBuilder md = new StringBuilder();
        md.append("# 推荐离线评估结果（脚本自动生成）\n\n");
        md.append("- 生成时间：").append(LocalDateTime.now()).append('\n');
        md.append("- 随机种子：").append(SEED).append("（同种子同结果，可复现）\n");
        md.append("- 历史截止时间（cutoff）：").append(cutoff)
                .append("（之后 ").append(TEST_WINDOW_DAYS).append(" 天的行为仅作答案）\n");
        md.append("- 合成数据：").append(syntheticUsers).append(" 个用户 / ")
                .append(SYNTHETIC_ITEMS).append(" 件物品 / ").append(syntheticOrders).append(" 条已完成订单\n");
        md.append("- 真实库现状：").append(realUsers).append(" 个用户 / ")
                .append(realOrders).append(" 条订单（不足以做时间切分，故用合成数据）\n\n");
        md.append("| 权重方案 | 参与评估用户 | 冷启动用户 | 命中用户 | 命中件数 / 测试集件数 | HitRate@").append(TOP_N)
                .append(" | Precision@").append(TOP_N).append(" | Recall@").append(TOP_N).append(" |\n");
        md.append("| :-- | --: | --: | --: | --: | --: | --: | --: |\n");
        for (Row row : rows) {
            md.append("| ").append(row.label())
                    .append(" | ").append(row.evaluatedUsers())
                    .append(" | ").append(row.coldStartUsers())
                    .append(" | ").append(row.usersWithHit())
                    .append(" | ").append(row.totalHits()).append(" / ").append(row.totalTestItems())
                    .append(" | ").append(percent(row.hitRate()))
                    .append(" | ").append(percent(row.precision()))
                    .append(" | ").append(percent(row.recall()))
                    .append(" |\n");
        }
        rows.forEach(row -> log.info("评估：{} → HitRate@{}={} Precision={} Recall={}（用户 {}，命中 {}）",
                row.label(), TOP_N, percent(row.hitRate()), percent(row.precision()),
                percent(row.recall()), row.evaluatedUsers(), row.usersWithHit()));

        Path target = Path.of("target", "eval");
        Files.createDirectories(target);
        Path file = target.resolve("recommend-eval.md");
        Files.writeString(file, md.toString(), StandardCharsets.UTF_8);
        log.info("评估报告已写入 {}", file.toAbsolutePath());
    }

    private static String percent(double value) {
        return String.format("%.2f%%", value * 100);
    }

    /** 一行结果（record：不可变、字段即列，写表时一一对应）。 */
    private record Row(String label,
                       int evaluatedUsers,
                       int coldStartUsers,
                       int usersWithHit,
                       int totalHits,
                       int totalTestItems,
                       double hitRate,
                       double precision,
                       double recall) {
    }
}
