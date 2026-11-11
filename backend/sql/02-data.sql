-- ============================================================================
-- 社区物品租用平台 · 初始化数据 02-data.sql
-- 依赖：01-schema.sql 已执行
-- 版本：v1.0   日期：2026-11-11
-- ----------------------------------------------------------------------------
-- 口令说明（明文仅本文件注释可见，库里只存 BCrypt 哈希）：
--   管理员  admin      / admin123   → $2a$10$.Fd3nrnJjy3D2fcDfNDUiu8UcUYZzOaMzjzX739pb/1nVcHuar/T.
--   普通用户 13800000001 / 123456   → $2a$10$8mBXeUf33mA0s8iUwDzX6.gFxjc7XiFxVPY2G1YAph/ERMTa.UZAy
-- 三张策略表固定 id = 1（单行表），PolicyLoader 直接 WHERE id = 1。
-- ============================================================================

SET NAMES utf8mb4;
USE rental;

-- ---------------------------------------------------------------------------
-- 1. 角色
-- ---------------------------------------------------------------------------
INSERT INTO `role` (`id`, `code`, `name`, `description`) VALUES
(1, 'USER',  '普通用户', '注册即得，可租可出租'),
(2, 'ADMIN', '管理员',   '审核物品、处理申诉、执行逾期扫描');

-- ---------------------------------------------------------------------------
-- 2. 用户（口令见文件头注释）
-- ---------------------------------------------------------------------------
INSERT INTO `user` (`id`, `nickname`, `phone`, `password_hash`, `community`, `credit_score`, `status`) VALUES
(1, '平台管理员', '13800000000', '$2a$10$.Fd3nrnJjy3D2fcDfNDUiu8UcUYZzOaMzjzX739pb/1nVcHuar/T.', '阳光社区', 100, 'NORMAL'),
(2, '张伟',       '13800000001', '$2a$10$8mBXeUf33mA0s8iUwDzX6.gFxjc7XiFxVPY2G1YAph/ERMTa.UZAy', '阳光社区', 82,  'NORMAL'),
(3, '李娜',       '13800000002', '$2a$10$8mBXeUf33mA0s8iUwDzX6.gFxjc7XiFxVPY2G1YAph/ERMTa.UZAy', '阳光社区', 74,  'NORMAL'),
(4, '王强',       '13800000003', '$2a$10$8mBXeUf33mA0s8iUwDzX6.gFxjc7XiFxVPY2G1YAph/ERMTa.UZAy', '绿洲社区', 45,  'NORMAL');

-- 管理员同时具备 USER 与 ADMIN，普通用户只有 USER
INSERT INTO `user_role` (`user_id`, `role_id`) VALUES
(1, 2), (1, 1),
(2, 1), (3, 1), (4, 1);

-- ---------------------------------------------------------------------------
-- 3. 类目（两级树；loss_coef 对应 loss.coef.category）
-- ---------------------------------------------------------------------------
INSERT INTO `category` (`id`, `name`, `parent_id`, `level`, `loss_coef`, `sort_no`) VALUES
(1, '电子数码', 0, 1, 0.0300, 10),
(2, '户外露营', 0, 1, 0.0200, 20),
(3, '工具五金', 0, 1, 0.0150, 30),
(4, '图书文具', 0, 1, 0.0080, 40),
(5, '家居生活', 0, 1, 0.0120, 50),
(6,  '相机',   1, 2, 0.0350, 11),
(7,  '耳机',   1, 2, 0.0280, 12),
(8,  '帐篷',   2, 2, 0.0250, 21),
(9,  '睡袋',   2, 2, 0.0200, 22),
(10, '电钻',   3, 2, 0.0180, 31),
(11, '小说',   4, 2, 0.0060, 41);

-- ---------------------------------------------------------------------------
-- 4. 物品（不同 owner、不同类目、不同状态，覆盖列表/筛选/推荐演示）
-- ---------------------------------------------------------------------------
INSERT INTO `item` (`id`, `owner_id`, `category_id`, `title`, `description`, `cover_url`, `community`, `daily_price`, `deposit`, `overdue_rate`, `loss_value`, `status`, `rent_count`, `view_count`) VALUES
(1, 2, 8, '双人帐篷（3 秒速开）', '适合春日郊野露营，含防潮垫，轻微使用痕迹', '/uploads/202611/tent-01.jpg', '阳光社区', 25.00, 100.00, 0.50, 0.1200, 'ON_SHELF', 6, 88),
(2, 2, 6, '索尼 A6400 微单套机', '含 16-50 套头与两块电池，仅限同城当面交接', '/uploads/202611/camera-01.jpg', '阳光社区', 60.00, 500.00, 0.80, 0.2500, 'ON_SHELF', 3, 152),
(3, 3, 10, '博世电钻套装', '含 12 支钻头，适合家具组装与墙面打孔', '/uploads/202611/drill-01.jpg', '阳光社区', 15.00, 80.00, 0.50, 0.0800, 'ON_SHELF', 9, 61),
(4, 3, 11, '《三体》全集（三册）', '书脊完好，无笔记划线', '/uploads/202611/book-01.jpg', '阳光社区', 3.00, 20.00, 0.30, 0.0300, 'ON_SHELF', 2, 34),
(5, 4, 9, '羽绒睡袋（-5℃）', '适合秋冬露营，已送洗', '/uploads/202611/bag-01.jpg', '绿洲社区', 18.00, 120.00, 0.50, 0.1500, 'ON_SHELF', 1, 27),
(6, 4, 7, 'AirPods Pro 2', '含充电盒与耳塞三副', '/uploads/202611/airpods-01.jpg', '绿洲社区', 12.00, 200.00, 0.80, 0.4000, 'PENDING', 0, 12),
(7, 2, 2, '折叠营地车', '承重 80kg，适合搬运露营装备', '/uploads/202611/cart-01.jpg', '阳光社区', 20.00, 150.00, 0.50, 0.0500, 'ON_SHELF', 4, 45);

-- ---------------------------------------------------------------------------
-- 4.1 物品图片（每件 ≥1 张，id 升序即展示顺序；cover_url 必须等于第一张）
-- ---------------------------------------------------------------------------
INSERT INTO `item_image` (`item_id`, `url`) VALUES
(1, '/uploads/202611/tent-01.jpg'),
(1, '/uploads/202611/tent-02.jpg'),
(1, '/uploads/202611/tent-03.jpg'),
(2, '/uploads/202611/camera-01.jpg'),
(2, '/uploads/202611/camera-02.jpg'),
(3, '/uploads/202611/drill-01.jpg'),
(3, '/uploads/202611/drill-02.jpg'),
(4, '/uploads/202611/book-01.jpg'),
(5, '/uploads/202611/bag-01.jpg'),
(5, '/uploads/202611/bag-02.jpg'),
(6, '/uploads/202611/airpods-01.jpg'),
(7, '/uploads/202611/cart-01.jpg'),
(7, '/uploads/202611/cart-02.jpg');

-- ---------------------------------------------------------------------------
-- 5. 策略表（单行，值与需求总结 §5 默认值一致）
-- ---------------------------------------------------------------------------
INSERT INTO `credit_policy` (`id`) VALUES (1);
INSERT INTO `fee_policy` (`id`) VALUES (1);
INSERT INTO `loss_policy` (`id`) VALUES (1);

-- ---------------------------------------------------------------------------
-- 6. 系统参数（异构零散配置）
-- ---------------------------------------------------------------------------
INSERT INTO `sys_config` (`config_key`, `config_value`, `value_type`, `config_group`, `description`) VALUES
('recommend.w.cf',          '0.6',        'DECIMAL', 'recommend', '协同过滤权重'),
('recommend.w.content',     '0.3',        'DECIMAL', 'recommend', '内容特征权重'),
('recommend.w.heat',        '0.1',        'DECIMAL', 'recommend', '热度权重'),
('recommend.decay.half_life', '30',       'INT',     'recommend', '行为时间衰减半衰期（天）'),
('recommend.heat.window',   '30',         'INT',     'recommend', '热度统计窗口（天）'),
('recommend.top_n',         '10',         'INT',     'recommend', '推荐返回条数'),
('order.lock.ttl',          '10',         'INT',     'order',     '物品锁 TTL（秒）'),
('order.scan.cron',         '0 0 * * * ?', 'CRON',   'order',     '逾期扫描表达式'),
('order.max_days',          '30',         'INT',     'order',     '单笔订单最长租期（天）');

-- ---------------------------------------------------------------------------
-- 7. 演示订单（覆盖三种"占用形态"，供前端联调与 K2 断言使用）
--    订单 1：COMPLETED —— 终态，占用已释放，结算/流水/信誉/损耗齐全
--    订单 2：CONFIRMED —— 占用存在，且必须【逐日 3 行】（11-20/21/22）
--    订单 3：PENDING   —— 不占时段（这是状态机的明确规定）
-- ---------------------------------------------------------------------------
INSERT INTO `rental_order` (`id`, `order_no`, `item_id`, `owner_id`, `renter_id`, `start_date`, `end_date`, `rent_days`,
                            `actual_return_date`, `status`, `daily_price`, `deposit`, `overdue_rate`, `rent_amount`,
                            `overdue_days`, `confirmed_at`, `in_use_at`, `completed_at`, `created_at`) VALUES
(1, 'R202610050001', 1, 2, 3, '2026-10-05', '2026-10-08', 3, '2026-10-08', 'COMPLETED', 25.00, 100.00, 0.5000, 75.00, 0,
    '2026-10-04 11:00:00', '2026-10-05 09:00:00', '2026-10-08 18:00:00', '2026-10-04 10:00:00'),
(2, 'R202611200001', 2, 2, 4, '2026-11-20', '2026-11-23', 3, NULL, 'CONFIRMED', 60.00, 500.00, 0.8000, 180.00, 0,
    '2026-11-15 09:30:00', NULL, NULL, '2026-11-15 09:00:00'),
(3, 'R202611250001', 3, 3, 2, '2026-11-25', '2026-11-27', 2, NULL, 'PENDING', 15.00, 80.00, 0.5000, 30.00, 0,
    NULL, NULL, NULL, '2026-11-16 20:00:00');

-- 订单 2 的占用：11-20 ~ 11-23 半开区间 → 11-20 / 11-21 / 11-22 共 3 行
INSERT INTO `order_occupancy` (`item_id`, `order_id`, `occupy_date`) VALUES
(2, 2, '2026-11-20'),
(2, 2, '2026-11-21'),
(2, 2, '2026-11-22');

-- 订单 1 的结算：租金 75 + 押金 100，损耗 0.03（押金 × 0.0003），应退 99.97
INSERT INTO `settlement` (`order_id`, `settle_no`, `rent_amount`, `deposit_amount`, `overdue_days`, `overdue_fee`,
                          `loss_ratio`, `loss_fee`, `refund_deposit`, `status`, `settled_at`) VALUES
(1, 'S202610080001', 75.00, 100.00, 0, 0.00, 0.0003, 0.03, 99.97, 'SETTLED', '2026-10-08 18:00:00');

INSERT INTO `fee_flow` (`flow_no`, `order_id`, `settlement_id`, `type`, `direction`, `amount`, `created_at`) VALUES
('F202610080001', 1, 1, 'RENT',    'IN',  75.00, '2026-10-08 18:00:00'),
('F202610080002', 1, 1, 'DEPOSIT', 'IN', 100.00, '2026-10-08 18:00:00'),
('F202610080003', 1, 1, 'LOSS',    'IN',   0.03, '2026-10-08 18:00:00'),
('F202610080004', 1, 1, 'REFUND',  'OUT', 99.97, '2026-10-08 18:00:00');

-- 一次履约双方各记一条（K1：信誉分绑定订单，不绑定角色）
INSERT INTO `credit_log` (`user_id`, `order_id`, `change`, `before_score`, `after_score`, `reason_code`, `reason_desc`) VALUES
(3, 1,  2, 72, 74, 'ON_TIME', '按时归还，信誉 +2'),
(2, 1,  2, 80, 82, 'ON_TIME', '物品按时收回，信誉 +2');

INSERT INTO `loss_log` (`item_id`, `order_id`, `term_coef`, `category_coef`, `return_coef`, `weight`,
                        `loss_before`, `loss_after`, `return_state`, `action`) VALUES
(1, 1, 0.0100, 0.0250, 1.0000, 0.0003, 0.1197, 0.1200, 'INTACT', 'NONE');

INSERT INTO `message` (`user_id`, `type`, `title`, `content`, `biz_type`, `biz_id`) VALUES
(2, 'ORDER', '物品已归还', '订单 R202610050001 已完成结算，押金 99.97 元已退还承租方。', 'ORDER', 1),
(3, 'ORDER', '押金已退还', '订单 R202610050001 结算完成：租金 75.00，损耗 0.03，退还押金 99.97 元。', 'ORDER', 1);

-- ============================================================================
-- 初始化完成：角色 2 / 用户 4 / 类目 11 / 物品 7 / 策略 3 / 参数 9 / 订单 3
-- ============================================================================
