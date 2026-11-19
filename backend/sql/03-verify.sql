-- ============================================================================
-- 社区物品租用平台 · 结构自检脚本 03-verify.sql
-- 用法：mysql -uroot -p rental < 03-verify.sql   （每行输出 PASS / FAIL）
-- 说明：这里只做【能查出来的】断言；并发、幂等等需要应用层配合的项在单测里验。
-- ============================================================================

SET NAMES utf8mb4;
USE rental;

-- A1 表数量必须为 19
SELECT IF(cnt = 19, 'PASS', 'FAIL') AS `A1_table_count`, cnt AS `actual`
FROM (SELECT COUNT(*) AS cnt FROM information_schema.tables
      WHERE table_schema = 'rental' AND table_type = 'BASE TABLE') t;

-- A2 存在占用的订单，占用行数必须等于租期天数（K2「逐日展开」的静态证据）
SELECT IF(COUNT(*) = 0, 'PASS', 'FAIL') AS `A2_occupancy_days_match`,
       IFNULL(GROUP_CONCAT(order_no), '-') AS `bad_orders`
FROM (SELECT o.order_no
      FROM rental_order o
      LEFT JOIN (SELECT order_id, COUNT(*) AS c FROM order_occupancy GROUP BY order_id) x
             ON x.order_id = o.id
      WHERE o.status IN ('PENDING', 'CONFIRMED', 'IN_USE', 'OVERDUE')
        AND IFNULL(x.c, 0) <> o.rent_days) t;

-- A3 终态订单（COMPLETED / CANCELLED）不得残留占用
SELECT IF(COUNT(*) = 0, 'PASS', 'FAIL') AS `A3_terminal_no_occupancy`, COUNT(*) AS `actual`
FROM order_occupancy oc
JOIN rental_order o ON o.id = oc.order_id
WHERE o.status IN ('COMPLETED', 'CANCELLED');

-- A4 待确认订单（PENDING）**必须**有占用 —— 「下单即占时段」；
--    若反过来要求 PENDING 不占，待确认期间同一时段能被重复下单，K2 直接破
SELECT IF(COUNT(*) = 0, 'PASS', 'FAIL') AS `A4_pending_must_occupy`, COUNT(*) AS `actual`
FROM rental_order o
LEFT JOIN (SELECT order_id, COUNT(*) AS c FROM order_occupancy GROUP BY order_id) x
       ON x.order_id = o.id
WHERE o.status = 'PENDING'
  AND IFNULL(x.c, 0) <> o.rent_days;

-- A5 应退押金 = max(押金 − 逾期费 − 损耗费, 0)
SELECT IF(COUNT(*) = 0, 'PASS', 'FAIL') AS `A5_refund_formula`, COUNT(*) AS `actual`
FROM settlement
WHERE refund_deposit <> GREATEST(deposit_amount - overdue_fee - loss_fee, 0);

-- A6 订单租金 = 日租金快照 × 计费天数
SELECT IF(COUNT(*) = 0, 'PASS', 'FAIL') AS `A6_rent_amount`, COUNT(*) AS `actual`
FROM rental_order
WHERE rent_amount <> daily_price * rent_days;

-- A7 信誉流水：后值 = clamp(前值 + 变动, 0, 100)
SELECT IF(COUNT(*) = 0, 'PASS', 'FAIL') AS `A7_credit_clamp`, COUNT(*) AS `actual`
FROM credit_log
WHERE after_score <> LEAST(GREATEST(before_score + `change`, 0), 100);

-- A8 有信誉流水的用户，当前分值必须等于其最后一条流水的后值
--    （没有流水的用户只校验在 [0,100] 区间内，由 ck_user_credit 保证，这里不重复）
SELECT IF(COUNT(*) = 0, 'PASS', 'FAIL') AS `A8_user_score_consistent`, COUNT(*) AS `actual`
FROM (
    SELECT u.id
    FROM `user` u
    JOIN (SELECT user_id, MAX(id) AS last_id FROM credit_log GROUP BY user_id) l ON l.user_id = u.id
    JOIN credit_log c ON c.id = l.last_id
    WHERE u.credit_score <> c.after_score
) t;

-- A9 金额列不得出现 float / double（金额一律 DECIMAL）
SELECT IF(COUNT(*) = 0, 'PASS', 'FAIL') AS `A9_no_float_column`, COUNT(*) AS `actual`
FROM information_schema.columns
WHERE table_schema = 'rental' AND data_type IN ('float', 'double');

-- A10 全库必须是 utf8mb4
SELECT IF(COUNT(*) = 0, 'PASS', 'FAIL') AS `A10_charset_utf8mb4`, COUNT(*) AS `actual`
FROM information_schema.tables
WHERE table_schema = 'rental' AND table_collation NOT LIKE 'utf8mb4%';

-- A11 CHECK 约束数量（MySQL 8.0.16+ 才真正生效；期望 >= 18）
SELECT IF(COUNT(*) >= 18, 'PASS', 'FAIL') AS `A11_check_constraints`, COUNT(*) AS `actual`
FROM information_schema.table_constraints
WHERE table_schema = 'rental' AND constraint_type = 'CHECK';

-- A12 K2/K3/K4 关键唯一键必须存在（9 个：含社区字典的"同城同片区小区名唯一"）
SELECT IF(COUNT(DISTINCT index_name) = 9, 'PASS', 'FAIL') AS `A12_key_unique_index`, COUNT(DISTINCT index_name) AS `actual`
FROM information_schema.statistics
WHERE table_schema = 'rental' AND non_unique = 0
  AND index_name IN ('uk_occupancy', 'uk_order_no', 'uk_settle_no', 'uk_settle_order',
                     'uk_credit_biz', 'uk_flow_no', 'uk_user_phone', 'uk_config_key',
                     'uk_community_city_district_name');

-- A13 没有物理外键（本项目约定由应用层保证）
SELECT IF(COUNT(*) = 0, 'PASS', 'FAIL') AS `A13_no_foreign_key`, COUNT(*) AS `actual`
FROM information_schema.table_constraints
WHERE table_schema = 'rental' AND constraint_type = 'FOREIGN KEY';

-- A14 策略表必须恰好各 1 行
SELECT IF((SELECT COUNT(*) FROM credit_policy) = 1
      AND (SELECT COUNT(*) FROM fee_policy) = 1
      AND (SELECT COUNT(*) FROM loss_policy) = 1, 'PASS', 'FAIL') AS `A14_policy_single_row`;

-- A15 每件物品至少 1 张图（需求总结 FR-M1-01「图片≥1」）
SELECT IF(COUNT(*) = 0, 'PASS', 'FAIL') AS `A15_item_has_image`, IFNULL(GROUP_CONCAT(id), '-') AS `bad_items`
FROM item i
WHERE i.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM item_image img WHERE img.item_id = i.id);

-- A16 item.cover_url 必须等于该物品 id 最小的那张图（否则列表与详情首图不一致）
SELECT IF(COUNT(*) = 0, 'PASS', 'FAIL') AS `A16_cover_is_first`, IFNULL(GROUP_CONCAT(i.id), '-') AS `bad_items`
FROM item i
JOIN (SELECT item_id, MIN(id) AS min_id FROM item_image GROUP BY item_id) m ON m.item_id = i.id
JOIN item_image f ON f.id = m.min_id
WHERE i.cover_url IS NULL OR i.cover_url <> f.url;

-- A17 图片路径必须是本地静态资源相对路径（FR-S-06：/uploads/{yyyyMM}/，不存外链、不存二进制）
SELECT IF(COUNT(*) = 0, 'PASS', 'FAIL') AS `A17_image_path_shape`, COUNT(*) AS `actual`
FROM item_image
WHERE url NOT LIKE '/uploads/%' OR CHAR_LENGTH(url) > 255;

-- A18 物品的 community_id 必须指向真实存在的社区（无物理外键，靠本断言兜底）
SELECT IF(COUNT(*) = 0, 'PASS', 'FAIL') AS `A18_item_community_exists`, IFNULL(GROUP_CONCAT(i.id), '-') AS `bad_items`
FROM item i
LEFT JOIN community c ON c.id = i.community_id
WHERE c.id IS NULL;

-- A19 用户填了社区就必须真实存在（community_id 允许为 NULL：未填写则不参与同小区优先排序）
SELECT IF(COUNT(*) = 0, 'PASS', 'FAIL') AS `A19_user_community_exists`, IFNULL(GROUP_CONCAT(u.id), '-') AS `bad_users`
FROM `user` u
LEFT JOIN community c ON c.id = u.community_id
WHERE u.community_id IS NOT NULL AND c.id IS NULL;

-- 参考信息：各表行数
SELECT 'user' AS tbl, COUNT(*) AS rows_cnt FROM `user`
UNION ALL SELECT 'role', COUNT(*) FROM `role`
UNION ALL SELECT 'community', COUNT(*) FROM `community`
UNION ALL SELECT 'category', COUNT(*) FROM `category`
UNION ALL SELECT 'item', COUNT(*) FROM `item`
UNION ALL SELECT 'item_image', COUNT(*) FROM `item_image`
UNION ALL SELECT 'rental_order', COUNT(*) FROM `rental_order`
UNION ALL SELECT 'order_occupancy', COUNT(*) FROM `order_occupancy`
UNION ALL SELECT 'settlement', COUNT(*) FROM `settlement`
UNION ALL SELECT 'fee_flow', COUNT(*) FROM `fee_flow`
UNION ALL SELECT 'credit_log', COUNT(*) FROM `credit_log`
UNION ALL SELECT 'loss_log', COUNT(*) FROM `loss_log`
UNION ALL SELECT 'message', COUNT(*) FROM `message`
UNION ALL SELECT 'sys_config', COUNT(*) FROM `sys_config`;
