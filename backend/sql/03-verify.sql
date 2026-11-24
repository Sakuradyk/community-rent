-- ============================================================================
-- 社区物品租用平台 · 结构自检脚本 03-verify.sql
-- 用法：mysql -uroot -p rental < 03-verify.sql   （每行输出 PASS / FAIL）
-- 说明：这里只做【能查出来的】断言；并发、幂等等需要应用层配合的项在单测里验。
-- ============================================================================

SET NAMES utf8mb4;
USE rental;

-- A1 表数量必须为 19
SELECT IF(cnt = 21, 'PASS', 'FAIL') AS `A1_table_count`, cnt AS `actual`
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
-- A20 结算单状态只能是冷静期四态（PENDING_CONFIRM / SETTLED / DISPUTED / ADJUSTED）
SELECT IF(COUNT(*) = 0, 'PASS', 'FAIL') AS `A20_settlement_status_whitelist`, IFNULL(GROUP_CONCAT(id), '-') AS `bad_rows`
FROM settlement
WHERE status NOT IN ('PENDING_CONFIRM', 'SETTLED', 'DISPUTED', 'ADJUSTED');

-- A21 冷静期字段与状态必须自洽
--   PENDING_CONFIRM / DISPUTED 必须有 confirm_deadline（否则永远扫不到超时）
--   SETTLED / ADJUSTED 必须有 settled_at（否则"什么时候结清的"无从追溯）
SELECT IF(COUNT(*) = 0, 'PASS', 'FAIL') AS `A21_settlement_confirm_fields`, IFNULL(GROUP_CONCAT(id), '-') AS `bad_rows`
FROM settlement
WHERE (status IN ('PENDING_CONFIRM', 'DISPUTED') AND confirm_deadline IS NULL)
   OR (status IN ('SETTLED', 'ADJUSTED') AND settled_at IS NULL);

-- A22 信誉流水的前值/后值必须与变动值自洽，且落在 [0,100]
SELECT IF(COUNT(*) = 0, 'PASS', 'FAIL') AS `A22_credit_log_consistent`, IFNULL(GROUP_CONCAT(id), '-') AS `bad_rows`
FROM credit_log
WHERE after_score <> LEAST(100, GREATEST(0, before_score + `change`));

-- A23 归还后订单转 COMPLETED 时不得残留占用（否则那几天永久占死）
SELECT IF(COUNT(*) = 0, 'PASS', 'FAIL') AS `A23_terminal_order_no_occupancy`, IFNULL(GROUP_CONCAT(o.order_id), '-') AS `bad_rows`
FROM order_occupancy o
JOIN rental_order r ON r.id = o.order_id
WHERE r.status IN ('COMPLETED', 'CANCELLED');

-- A24 归还申请与订单状态必须自洽
--   ① 未取用的订单（PENDING / CONFIRMED）不应该有归还申请
--   ② 有归还申请就必须有申报的归还状态（否则超时自动结算时不知道按什么状态算损耗）
--   ③ 申报状态只能是三态之一
SELECT IF(COUNT(*) = 0, 'PASS', 'FAIL') AS `A24_return_request_consistent`, IFNULL(GROUP_CONCAT(r.id), '-') AS `bad_orders`
FROM rental_order r
WHERE (r.status IN ('PENDING', 'CONFIRMED') AND r.return_requested_at IS NOT NULL)
   OR (r.return_requested_at IS NOT NULL
       AND (r.return_requested_state IS NULL
            OR r.return_requested_state NOT IN ('INTACT', 'WORN', 'DAMAGED')));

-- A25 申诉裁决必须留下裁定类型
--   双方都能申诉，「支持」对双方方向相反，所以裁定必须能区分是谁的主张成立（不能用布尔）
SELECT IF(COUNT(*) = 0, 'PASS', 'FAIL') AS `A25_verdict_type_present`, IFNULL(GROUP_CONCAT(a.id), '-') AS `bad_appeals`
FROM appeal a
WHERE a.status IN ('ACCEPTED', 'REJECTED')
  AND (a.verdict_type IS NULL
       OR a.verdict_type NOT IN ('RENTER_WIN', 'OWNER_WIN', 'PARTIAL', 'REJECTED'));

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
UNION ALL SELECT 'appeal', COUNT(*) FROM `appeal`
UNION ALL SELECT 'message', COUNT(*) FROM `message`
UNION ALL SELECT 'sys_config', COUNT(*) FROM `sys_config`;

-- ----------------------------------------------------------------------------
-- A26 账户主体合法：owner_type 只能 USER/EXTERNAL；USER 行必须指向存在的用户；EXTERNAL 只有一行
-- ----------------------------------------------------------------------------
SELECT 'A26_account_owner_valid' AS check_item,
       CASE WHEN COUNT(*) = 0 THEN 'PASS' ELSE 'FAIL' END AS result,
       COUNT(*) AS bad_rows
FROM `account` a
WHERE a.owner_type NOT IN ('USER', 'EXTERNAL')
   OR (a.owner_type = 'USER' AND NOT EXISTS (SELECT 1 FROM `user` u WHERE u.id = a.owner_id))
   OR (a.owner_type = 'EXTERNAL' AND a.owner_id <> 0);

SELECT 'A26b_single_external' AS check_item,
       CASE WHEN COUNT(*) = 1 THEN 'PASS' ELSE 'FAIL' END AS result,
       COUNT(*) AS external_rows
FROM `account` WHERE owner_type = 'EXTERNAL';

-- ----------------------------------------------------------------------------
-- A27 全站守恒：所有账户（可用 + 冻结）之和必须为 0（EXTERNAL 为负即代表净流入）
-- ----------------------------------------------------------------------------
SELECT 'A27_global_conservation' AS check_item,
       CASE WHEN ABS(SUM(cash_balance + frozen_balance)) < 0.005 THEN 'PASS' ELSE 'FAIL' END AS result,
       SUM(cash_balance + frozen_balance) AS total
FROM `account`;

-- ----------------------------------------------------------------------------
-- A28 逐账户对账：每个账户的两个余额都必须等于其分录累计（只追加、可重算）
-- ----------------------------------------------------------------------------
SELECT 'A28_balance_reconcile' AS check_item,
       CASE WHEN COUNT(*) = 0 THEN 'PASS' ELSE 'FAIL' END AS result,
       COUNT(*) AS mismatched_accounts
FROM (
    SELECT a.id,
           a.cash_balance,
           COALESCE((SELECT SUM(e.amount) FROM `account_entry` e
                      WHERE e.to_account_id = a.id AND e.to_bucket = 'CASH'), 0)
         - COALESCE((SELECT SUM(e.amount) FROM `account_entry` e
                      WHERE e.from_account_id = a.id AND e.from_bucket = 'CASH'), 0) AS calc_cash,
           a.frozen_balance,
           COALESCE((SELECT SUM(e.amount) FROM `account_entry` e
                      WHERE e.to_account_id = a.id AND e.to_bucket = 'FROZEN'), 0)
         - COALESCE((SELECT SUM(e.amount) FROM `account_entry` e
                      WHERE e.from_account_id = a.id AND e.from_bucket = 'FROZEN'), 0) AS calc_frozen
    FROM `account` a
) t
WHERE ABS(t.cash_balance - t.calc_cash) >= 0.005
   OR ABS(t.frozen_balance - t.calc_frozen) >= 0.005;
-- ----------------------------------------------------------------------------
-- A29 冻结对账：每个用户的冻结余额必须等于其未完结订单的租金 + 押金之和
--     未完结 = PENDING / CONFIRMED / IN_USE / OVERDUE（结算完、取消后冻结应为 0）
--     这条抓的是「冻结悬空」：订单被删/钱没解冻时，A27 照样守恒、A28 照样能回放，
--     只有账户的 frozen 与在途订单对不上（2026-11-23 张伟账户两笔 29.00 即此类）
-- ----------------------------------------------------------------------------
SELECT 'A29_frozen_matches_open_orders' AS check_item,
       CASE WHEN COUNT(*) = 0 THEN 'PASS' ELSE 'FAIL' END AS result,
       COUNT(*) AS mismatched_accounts
FROM (
    SELECT u.id AS uid,
           COALESCE(a.frozen_balance, 0) AS frozen,
           COALESCE((SELECT SUM(o.rent_amount + o.deposit)
                       FROM `rental_order` o
                      WHERE o.renter_id = u.id
                        AND o.status IN ('PENDING', 'CONFIRMED', 'IN_USE', 'OVERDUE')), 0) AS expect
    FROM `user` u
    LEFT JOIN `account` a ON a.owner_type = 'USER' AND a.owner_id = u.id
) t
WHERE ABS(t.frozen - t.expect) >= 0.005;