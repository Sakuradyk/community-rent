-- ============================================================================
-- 社区物品租用平台 · 建表脚本 01-schema.sql
-- 目标库：rental（utf8mb4）   版本：v1.1   日期：2026-11-16
-- ----------------------------------------------------------------------------
-- 设计口径见 docs/数据库设计.md，要点：
--   1) 不建物理外键，关联完整性由应用层 + 唯一索引保证（PROJECT_RULES.md §5）
--   2) 金额一律 DECIMAL(10,2)，损耗系数/比例一律 DECIMAL(6,4)
--   3) K2 的最后防线是 uk_occupancy(item_id, occupy_date)：占用必须【逐日展开】
--   4) 订单表名用 rental_order 而非 order —— order 是 SQL 保留字，全库反引号成本高
--   5) 只给"会被 WHERE / ORDER BY 用到"的列建索引，不建"看起来完整"的冗余索引
--   6) 社区用字典表 community，user/item 只存 community_id —— 消灭"阳光花园 / 阳光花园小区"这类别名歧义
-- ----------------------------------------------------------------------------
-- ⚠ 本脚本头部会 DROP 全部业务表，仅用于开发库重建。生产/有数据的库禁止直接执行。
-- ============================================================================

SET NAMES utf8mb4;
USE rental;

-- ---------------------------------------------------------------------------
-- 0. 清理（开发库重建用；顺序为依赖倒序）
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `message`;
DROP TABLE IF EXISTS `appeal`;
DROP TABLE IF EXISTS `loss_log`;
DROP TABLE IF EXISTS `credit_log`;
DROP TABLE IF EXISTS `fee_flow`;
DROP TABLE IF EXISTS `settlement`;
DROP TABLE IF EXISTS `order_occupancy`;
DROP TABLE IF EXISTS `rental_order`;
DROP TABLE IF EXISTS `item_image`;
DROP TABLE IF EXISTS `item`;
DROP TABLE IF EXISTS `category`;
DROP TABLE IF EXISTS `user_role`;
DROP TABLE IF EXISTS `role`;
DROP TABLE IF EXISTS `community`;
DROP TABLE IF EXISTS `user`;
DROP TABLE IF EXISTS `sys_config`;
DROP TABLE IF EXISTS `credit_policy`;
DROP TABLE IF EXISTS `fee_policy`;
DROP TABLE IF EXISTS `loss_policy`;

-- ===========================================================================
-- 一、账号与权限
-- ===========================================================================

-- ---------------------------------------------------------------------------
-- 1. user 用户账号表
-- 用途：只存"账号"，不存"角色身份"。出租方 / 承租方是订单里的双边角色，
--       由 order.owner_id / order.renter_id 表达，绝不在本表加 is_owner 字段（K1）。
-- ---------------------------------------------------------------------------
CREATE TABLE `user` (
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `nickname`      VARCHAR(32)     NOT NULL                COMMENT '昵称',
    `phone`         VARCHAR(20)     NOT NULL                COMMENT '手机号，登录名',
    `password_hash` VARCHAR(100)    NOT NULL                COMMENT 'BCrypt 哈希（$2a$10$ 开头，60 位）',
    `avatar_url`    VARCHAR(255)             DEFAULT NULL   COMMENT '头像地址',
    `community_id`  BIGINT UNSIGNED          DEFAULT NULL   COMMENT '所属社区，引用 community.id；NULL = 未填写，不参与同小区优先排序',
    `credit_score`  INT             NOT NULL DEFAULT 80     COMMENT '信誉分，初始值取 credit_policy.init_score',
    `status`        VARCHAR(16)     NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL 正常 / DISABLED 禁用',
    `last_login_at` DATETIME                 DEFAULT NULL   COMMENT '最近登录时间',
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT         NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 正常 / 1 已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_phone` (`phone`) COMMENT '登录名唯一；软删后手机号仍占用，不开放二次注册（毕设取舍）',
    KEY `idx_user_status` (`status`)     COMMENT '管理端按状态筛选用户',
    KEY `idx_user_community` (`community_id`) COMMENT '按社区筛人（推荐冷启动用）',
    CONSTRAINT `ck_user_credit` CHECK (`credit_score` BETWEEN 0 AND 100)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '用户账号表';

-- ---------------------------------------------------------------------------
-- 2. role 角色表
-- 用途：角色字典。本课题只有 USER / ADMIN 两种，但保留表结构以便扩展
--       （例如后续加 AUDITOR）。Spring Security 里映射成 ROLE_USER / ROLE_ADMIN。
-- ---------------------------------------------------------------------------
CREATE TABLE `role` (
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `code`        VARCHAR(32)     NOT NULL                COMMENT '角色码：USER / ADMIN',
    `name`        VARCHAR(32)     NOT NULL                COMMENT '角色名称',
    `description` VARCHAR(255)             DEFAULT NULL   COMMENT '说明',
    `created_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_code` (`code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '角色字典表';

-- ---------------------------------------------------------------------------
-- 3. user_role 用户-角色关联表
-- 用途：RBAC。一个用户可有多个角色（本课题：普通用户注册得 USER，管理员手工加 ADMIN）。
-- ---------------------------------------------------------------------------
CREATE TABLE `user_role` (
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`    BIGINT UNSIGNED NOT NULL                COMMENT '用户主键',
    `role_id`    BIGINT UNSIGNED NOT NULL                COMMENT '角色主键',
    `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '授予时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`) COMMENT '同一用户不可重复授予同一角色',
    KEY `idx_ur_role` (`role_id`)                    COMMENT '按角色反查用户（管理端）'
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '用户角色关联表';

-- ===========================================================================
-- 二、社区字典（user / item 共用）
-- ===========================================================================

-- ---------------------------------------------------------------------------
-- 4. community 社区字典表
-- 为什么要有这张表（把"社区"从自由文本升级为字典引用）：
--   1) 别名歧义：甲写"阳光花园"、乙写"阳光花园小区"，字符串相等比较会判成两个社区，
--      同小区的人互相看不到对方的物品。字典化后所有人从下拉选同一个 id，比较退化为整数相等。
--   2) 可管理：停用某小区后不再出现在下拉里，历史数据保留（不物理删除）。
--   3) 可排序：列表/推荐的"同小区 > 同片区 > 其他"三档，靠 community_id 与 district 两个等值
--      判断就能表达，不需要坐标、不需要距离计算（需求总结 §10 明确"地图选址、距离排序 → 不做"）。
-- 为什么 district 只存字符串而不是外键到"片区表"：片区不发号、不单独管理，只用于第二档排序，
--   一个字符串足够；真需要片区管理时再加表，不提前抽象。
-- 为什么 user.community_id 可空、item.community_id 不可空：注册不卡壳（不填则不启用优先档）；
--   物品必须能说清在哪个小区，否则"就近"无从谈起。
-- ---------------------------------------------------------------------------
CREATE TABLE `community` (
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`        VARCHAR(64)     NOT NULL                COMMENT '小区名，如 阳光花园',
    `district`    VARCHAR(64)     NOT NULL                COMMENT '所属片区/街道，用于排序第二档"同片区"',
    `city`        VARCHAR(64)     NOT NULL DEFAULT '本市'  COMMENT '所属城市',
    `alias`       VARCHAR(255)             DEFAULT NULL   COMMENT '别名，英文逗号分隔（仅管理端搜索用，不参与匹配）',
    `enabled`     TINYINT         NOT NULL DEFAULT 1      COMMENT '1 可选 / 0 停用（不再出现在下拉，历史引用保留）',
    `sort_no`     INT             NOT NULL DEFAULT 0      COMMENT '下拉展示顺序，越小越前',
    `created_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_community_city_district_name` (`city`, `district`, `name`) COMMENT '同城同片区内小区名唯一（从源头消灭别名重复登记）',
    KEY `idx_community_district` (`district`) COMMENT '同片区排序（列表页第二档）',
    KEY `idx_community_enabled` (`enabled`, `sort_no`) COMMENT '下拉只查启用项并按 sort_no 排序',
    CONSTRAINT `ck_community_enabled` CHECK (`enabled` IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '社区字典表';
-- ===========================================================================
-- 三、类目与物品
-- ===========================================================================

-- ---------------------------------------------------------------------------
-- 5. category 物品类目表
-- 用途：两级树。loss_coef 是该类目的损耗系数（对应 loss.coef.category），
--       放在类目上而不是配置表里 —— 因为它是"随类目走的属性"，不是全局阈值。
-- ---------------------------------------------------------------------------
CREATE TABLE `category` (
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`       VARCHAR(32)     NOT NULL                COMMENT '类目名称',
    `parent_id`  BIGINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '父类目，0 表示一级',
    `level`      TINYINT         NOT NULL DEFAULT 1      COMMENT '层级：1 一级 / 2 二级（最多两级）',
    `loss_coef`  DECIMAL(6, 4)   NOT NULL DEFAULT 0.0100 COMMENT '类目损耗系数，参与 w = 租期系数×类目系数×归还状态系数',
    `sort_no`    INT             NOT NULL DEFAULT 0      COMMENT '排序值，越小越前',
    `status`     VARCHAR(16)     NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL 启用 / DISABLED 停用',
    `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`    TINYINT         NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 正常 / 1 已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_category_parent_name` (`parent_id`, `name`) COMMENT '同级下类目名不重复',
    KEY `idx_category_parent` (`parent_id`),
    CONSTRAINT `ck_category_level` CHECK (`level` IN (1, 2)),
    CONSTRAINT `ck_category_loss_coef` CHECK (`loss_coef` >= 0 AND `loss_coef` <= 1)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '物品类目表';

-- ---------------------------------------------------------------------------
-- 6. item 物品表
-- 用途：待租物品。价格三件套（日租金/押金/逾期费率）在发布时确定；
--       订单创建时会把这三个值【快照】进 rental_order，之后改价不影响历史订单。
-- loss_value：该物品的累计损耗值 L ∈ [0,1]，达 loss.threshold.maintain/repair 触发提醒。
-- ---------------------------------------------------------------------------
CREATE TABLE `item` (
    `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `owner_id`       BIGINT UNSIGNED NOT NULL               COMMENT '物品归属人（与订单 owner_id 语义不同，见需求总结 §7.2）',
    `category_id`    BIGINT UNSIGNED NOT NULL               COMMENT '类目',
    `title`          VARCHAR(64)     NOT NULL               COMMENT '标题',
    `description`    VARCHAR(1000)            DEFAULT NULL  COMMENT '描述（推荐内容特征的来源）',
    `cover_url`      VARCHAR(255)             DEFAULT NULL  COMMENT '封面图（= item_image 里 id 最小的一张，冗余存一份以免列表页 join）',
    `community_id`   BIGINT UNSIGNED NOT NULL               COMMENT '所在社区，引用 community.id（发布时必填）',
    `daily_price`    DECIMAL(10, 2)  NOT NULL               COMMENT '日租金',
    `deposit`        DECIMAL(10, 2)  NOT NULL DEFAULT 0.00  COMMENT '押金',
    `overdue_rate`   DECIMAL(6, 4)   NOT NULL DEFAULT 0.5000 COMMENT '逾期费率（×日租金/天），须落在 fee_policy 的 [min,max] 内',
    `loss_value`     DECIMAL(6, 4)   NOT NULL DEFAULT 0.0000 COMMENT '累计损耗值 L ∈ [0,1]',
    `status`         VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING 待审 / ON_SHELF 在架 / OFF_SHELF 下架 / RENTED 租出中 / MAINTAINING 检修中 / REJECTED 审核驳回',
    `audit_remark`   VARCHAR(255)             DEFAULT NULL  COMMENT '审核意见',
    `audit_admin_id` BIGINT UNSIGNED          DEFAULT NULL  COMMENT '审核人',
    `audit_time`     DATETIME                 DEFAULT NULL  COMMENT '审核时间',
    `rent_count`     INT             NOT NULL DEFAULT 0     COMMENT '累计成交次数（热度特征，免去 count(*)）',
    `view_count`     INT             NOT NULL DEFAULT 0     COMMENT '累计浏览数（热度特征）',
    `created_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`        TINYINT         NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 正常 / 1 已删',
    PRIMARY KEY (`id`),
    KEY `idx_item_owner` (`owner_id`)                COMMENT '我的物品列表',
    KEY `idx_item_status_category` (`status`, `category_id`) COMMENT '列表页：在架 + 分类筛选（最常用组合）',
    KEY `idx_item_category` (`category_id`)          COMMENT '纯分类筛选 / 推荐内容特征',
    KEY `idx_item_community` (`community_id`)        COMMENT '同小区筛选与排序（列表页第一排序档）',
    CONSTRAINT `ck_item_daily_price` CHECK (`daily_price` > 0),
    CONSTRAINT `ck_item_deposit` CHECK (`deposit` >= 0),
    CONSTRAINT `ck_item_overdue_rate` CHECK (`overdue_rate` >= 0),
    CONSTRAINT `ck_item_loss_value` CHECK (`loss_value` >= 0 AND `loss_value` <= 1)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '物品表';

-- ---------------------------------------------------------------------------
-- 5.1 item_image 物品图片表
-- 用途：一件物品的多张图（需求总结 FR-M1-01「图片≥1」、FR-S-06 上传）。列表页不查本表，
--       直接用 item.cover_url；只有详情页做一次 `WHERE item_id = ? ORDER BY id`。
-- 为什么不做 sort_no：需求总结 §10 灰区裁定「多图排序」为 P2 —— 上传顺序即展示顺序，
--       用自增 id 排序即可，不引入排序字段与拖拽交互。
-- 为什么不存二进制：图片落本地磁盘 ${upload.dir}/{yyyyMM}/，库里只存相对路径
--       （需求总结 §2.3「图片存储 P0 = 本地磁盘 + 静态资源映射」）。
-- uk_item_image(item_id, url)：同一物品重复提交同一张图会被拦住，避免前端重复点上传。
-- ---------------------------------------------------------------------------
CREATE TABLE `item_image` (
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键（同时也是展示顺序）',
    `item_id`    BIGINT UNSIGNED NOT NULL               COMMENT '物品',
    `url`        VARCHAR(255)    NOT NULL               COMMENT '相对路径，如 /uploads/202611/abc.jpg',
    `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_item_image` (`item_id`, `url`) COMMENT '同一物品同一张图只存一次',
    KEY `idx_item_image_item` (`item_id`, `id`)   COMMENT '详情页按上传顺序取图',
    CONSTRAINT `ck_item_image_item` CHECK (`item_id` > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '物品图片表';

-- ===========================================================================
-- 四、订单与时段占用（K2 核心）
-- ===========================================================================

-- ---------------------------------------------------------------------------
-- 7. rental_order 订单表
-- 用途：一笔租借。含双边角色（owner_id / renter_id）、租期半开区间 [start_date, end_date)、
--       状态机、以及三个价格快照。
-- 命名：需求总结写 order，实际用 rental_order —— order 是 SQL 保留字。
-- 区间语义：[start_date, end_date)，10-01~10-04 表示占用 10-01/10-02/10-03 共 3 天。
-- ---------------------------------------------------------------------------
CREATE TABLE `rental_order` (
    `id`                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_no`           VARCHAR(32)     NOT NULL              COMMENT '业务单号，如 R202611110001',
    `item_id`            BIGINT UNSIGNED NOT NULL              COMMENT '物品',
    `owner_id`           BIGINT UNSIGNED NOT NULL              COMMENT '出租方（下单时从 item.owner_id 冗余快照，便于双边查询）',
    `renter_id`          BIGINT UNSIGNED NOT NULL              COMMENT '承租方',
    `start_date`         DATE            NOT NULL              COMMENT '起租日（含）',
    `end_date`           DATE            NOT NULL              COMMENT '归还日（不含）',
    `rent_days`          INT             NOT NULL              COMMENT '计费天数 = DATEDIFF(end_date, start_date)，不足 1 天按 1 天',
    `actual_return_date` DATE                     DEFAULT NULL COMMENT '实际归还日',
    `status`             VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / CONFIRMED / IN_USE / OVERDUE / COMPLETED / CANCELLED',
    `daily_price`        DECIMAL(10, 2)  NOT NULL              COMMENT '价格快照：日租金',
    `deposit`            DECIMAL(10, 2)  NOT NULL              COMMENT '价格快照：押金',
    `overdue_rate`       DECIMAL(6, 4)   NOT NULL              COMMENT '价格快照：逾期费率（×日租金/天）',
    `rent_amount`        DECIMAL(10, 2)  NOT NULL              COMMENT '租金 = daily_price × rent_days（下单时算出并冻结）',
    `overdue_days`       INT             NOT NULL DEFAULT 0    COMMENT '逾期天数（结算时写）',
    `return_requested_at`    DATETIME            DEFAULT NULL COMMENT '承租方发起归还申请的时间（状态不变，只记时间戳；用于出租方超时未确认时自动结算）',
    `return_requested_by`    VARCHAR(16)         DEFAULT NULL COMMENT '归还申请人：RENTER（当前只有承租方发起，留列便于将来扩展）',
    `return_requested_state` VARCHAR(16)         DEFAULT NULL COMMENT '承租方申报的归还状态：INTACT / WORN / DAMAGED（出租方确认时以自己验收的为准，超时自动结算时按申报值）',
    `cancel_reason`      VARCHAR(255)             DEFAULT NULL COMMENT '取消原因',
    `cancel_by`          VARCHAR(16)              DEFAULT NULL COMMENT 'RENTER / OWNER / ADMIN',
    `remark`             VARCHAR(255)             DEFAULT NULL COMMENT '备注',
    `confirmed_at`       DATETIME                 DEFAULT NULL COMMENT '确认时间',
    `in_use_at`          DATETIME                 DEFAULT NULL COMMENT '开始使用时间',
    `completed_at`       DATETIME                 DEFAULT NULL COMMENT '完成时间',
    `cancelled_at`       DATETIME                 DEFAULT NULL COMMENT '取消时间',
    `created_at`         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
    `update_time`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_order_item_status` (`item_id`, `status`) COMMENT '物品的订单列表 / 确认前复查该物品时段',
    KEY `idx_order_renter_status` (`renter_id`, `status`) COMMENT '我的租入',
    KEY `idx_order_owner_status` (`owner_id`, `status`)   COMMENT '我的租出',
    KEY `idx_order_scan` (`status`, `end_date`)           COMMENT '逾期扫描：未归还状态 + end_date 早于今天',
    KEY `idx_order_return_request` (`status`, `return_requested_at`) COMMENT '归还申请超时扫描：承租方已申报但出租方未确认',
    -- K1：自租禁止（数据库层第二道防线；第一道在 Service 参数校验 → 3006 SELF_RENT_FORBIDDEN）
    CONSTRAINT `ck_order_bilateral` CHECK (`owner_id` <> `renter_id`),
    CONSTRAINT `ck_order_date_range` CHECK (`start_date` < `end_date`),
    CONSTRAINT `ck_order_rent_days` CHECK (`rent_days` > 0),
    CONSTRAINT `ck_order_amounts` CHECK (`daily_price` > 0 AND `deposit` >= 0 AND `rent_amount` >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '订单表';

-- ---------------------------------------------------------------------------
-- 8. order_occupancy 时段占用表【K2 最后防线】
-- 用途：一笔跨 N 天的订单展开成 N 行，每行代表"某物品的某一天已被占用"。
--       唯一索引 uk_occupancy 让并发插入在同一 (item_id, occupy_date) 上必然冲突，
--       因此无论应用层预检 / 分布式锁如何失效，数据库都不会被超卖。
-- ⚠ 铁律：必须逐日展开（[start_date, end_date) 逐天一行）。若改成"一单一行存
--   start_date/end_date"，则任意两笔订单的 (item_id, start_date) 天然不同，
--   唯一索引永远不会触发，K2 第三层防线等于不存在。
-- 生命周期：**建单（PENDING）时**即按 [start_date, end_date) 逐日 INSERT ——
--   "下单即占时段"。若改成"确认后才占"或"一单一行"，待确认期间同一时段能被
--   重复下单，唯一索引没机会触发，K2 直接破。进入终态（COMPLETED / CANCELLED）
--   时 DELETE（取消/归还释放），中间 CONFIRMED / IN_USE / OVERDUE 一直保留。
-- ---------------------------------------------------------------------------
CREATE TABLE `order_occupancy` (
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `item_id`     BIGINT UNSIGNED NOT NULL                COMMENT '物品',
    `order_id`    BIGINT UNSIGNED NOT NULL                COMMENT '订单',
    `occupy_date` DATE            NOT NULL                COMMENT '被占用的那一天（单日粒度）',
    `created_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '写入时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_occupancy` (`item_id`, `occupy_date`) COMMENT 'K2 硬防线：同一物品同一天只能有一条占用',
    KEY `idx_occ_order` (`order_id`) COMMENT '释放占用：DELETE FROM order_occupancy WHERE order_id = ?'
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '物品时段占用表（逐日展开）';

-- ===========================================================================
-- 五、结算与费用流水（K3 核心）
-- ===========================================================================

-- ---------------------------------------------------------------------------
-- 9. settlement 结算单表
-- 用途：一单一结算（uk_order_id），结算单号唯一（uk_settle_no）保证幂等。
-- 公式：应退押金 = 押金 − 逾期费 − 损耗费，下限 0。
-- ---------------------------------------------------------------------------
CREATE TABLE `settlement` (
    `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_id`       BIGINT UNSIGNED NOT NULL               COMMENT '订单（一单一结算）',
    `settle_no`      VARCHAR(32)     NOT NULL               COMMENT '结算单号，幂等键',
    `rent_amount`    DECIMAL(10, 2)  NOT NULL DEFAULT 0.00  COMMENT '租金',
    `deposit_amount` DECIMAL(10, 2)  NOT NULL DEFAULT 0.00  COMMENT '押金（= 订单快照 deposit）',
    `overdue_days`   INT             NOT NULL DEFAULT 0     COMMENT '逾期天数',
    `overdue_fee`    DECIMAL(10, 2)  NOT NULL DEFAULT 0.00  COMMENT '逾期费 = Σ 日租金 × 阶梯费率',
    `loss_ratio`     DECIMAL(6, 4)   NOT NULL DEFAULT 0.0000 COMMENT '本次归还带来的损耗值（本次 w，非累计 L）',
    `loss_fee`       DECIMAL(10, 2)  NOT NULL DEFAULT 0.00  COMMENT '损耗费 = 押金 × 本次损耗值（可被策略调整）',
    `refund_deposit` DECIMAL(10, 2)  NOT NULL DEFAULT 0.00  COMMENT '应退押金 = max(押金 − 逾期费 − 损耗费, 0)',
    `status`         VARCHAR(16)     NOT NULL DEFAULT 'PENDING_CONFIRM' COMMENT 'PENDING_CONFIRM 待承租方确认 / SETTLED 已结算 / DISPUTED 有异议（申诉中）/ ADJUSTED 已被裁决调整',
    `confirm_deadline` DATETIME              DEFAULT NULL  COMMENT '冷静期截止 = 归还时间 + settlement.confirm.hours（PENDING_CONFIRM 必填）',
    `confirmed_at`   DATETIME                 DEFAULT NULL  COMMENT '承租方点「无异议」的时间',
    `disputed_at`    DATETIME                 DEFAULT NULL  COMMENT '承租方提出异议的时间',
    `operator_id`    BIGINT UNSIGNED          DEFAULT NULL  COMMENT '操作人（系统自动结算时为 NULL）',
    `remark`         VARCHAR(255)             DEFAULT NULL  COMMENT '备注',
    `settled_at`     DATETIME                 DEFAULT NULL  COMMENT '结算生效时间（SETTLED / ADJUSTED 时写入）',
    `created_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_settle_order` (`order_id`)  COMMENT '一单一结算，重复结算直接冲突',
    UNIQUE KEY `uk_settle_no` (`settle_no`),
    KEY `idx_settle_created` (`created_at`) COMMENT '结算列表按时间倒序',
  KEY `idx_settle_confirm_deadline` (`status`, `confirm_deadline`) COMMENT '冷静期超时扫描：status + confirm_deadline < now()，不用全表扫',
    CONSTRAINT `ck_settle_amounts` CHECK (`rent_amount` >= 0 AND `overdue_fee` >= 0 AND `loss_fee` >= 0 AND `refund_deposit` >= 0),
    CONSTRAINT `ck_settle_loss_ratio` CHECK (`loss_ratio` >= 0 AND `loss_ratio` <= 1),
    CONSTRAINT `ck_settle_overdue_days` CHECK (`overdue_days` >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '结算单表';

-- ---------------------------------------------------------------------------
-- 10. fee_flow 资金流水表
-- 用途：把结算拆成可对账的流水条目（租金/押金/逾期费/损耗费/退款），
--       毕设不接真实支付，这里记录"账面流水"，供演示与论文的账目推演。
-- ---------------------------------------------------------------------------
CREATE TABLE `fee_flow` (
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `flow_no`       VARCHAR(32)     NOT NULL               COMMENT '流水号，幂等键',
    `order_id`      BIGINT UNSIGNED NOT NULL               COMMENT '订单',
    `settlement_id` BIGINT UNSIGNED          DEFAULT NULL  COMMENT '归属结算单',
    `type`          VARCHAR(16)     NOT NULL               COMMENT 'RENT 租金 / DEPOSIT 押金 / OVERDUE 逾期费 / LOSS 损耗费 / REFUND 退还 / ADJUST 裁决冲正',
    `direction`     VARCHAR(8)      NOT NULL               COMMENT 'IN 收（平台/出租方收到）/ OUT 出（退给承租方）',
    `amount`        DECIMAL(10, 2)  NOT NULL               COMMENT '金额，恒为正数，方向看 direction',
    `remark`        VARCHAR(255)             DEFAULT NULL  COMMENT '备注',
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_flow_no` (`flow_no`),
    KEY `idx_flow_order` (`order_id`)      COMMENT '订单详情页展示流水',
    KEY `idx_flow_settlement` (`settlement_id`),
    CONSTRAINT `ck_flow_amount` CHECK (`amount` >= 0),
    CONSTRAINT `ck_flow_direction` CHECK (`direction` IN ('IN', 'OUT'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '资金流水表';

-- ===========================================================================
-- 六、信誉与损耗（K4 / K6）
-- ===========================================================================

-- ---------------------------------------------------------------------------
-- 11. credit_log 信誉分变动流水
-- 用途：每一次加减分一条记录，含前值/后值，可完整回溯（论文里"信誉分可审计"的证据）。
-- uk_credit_biz：同一用户在同一订单上的同一原因只能记一次 —— 防定时任务重跑、
--                防用户连点两次"确认归还"导致双倍加减分。
-- 注意：一次履约对双方各记一条，所以唯一键是 (user_id, order_id, reason_code) 而非 (order_id, reason_code)。
-- ---------------------------------------------------------------------------
CREATE TABLE `credit_log` (
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     BIGINT UNSIGNED NOT NULL                COMMENT '被加减分的用户',
    `order_id`    BIGINT UNSIGNED NOT NULL                COMMENT '关联订单（非空：信誉分绑定订单而非角色）',
    `change`      INT             NOT NULL                COMMENT '变动值，正加负减',
    `before_score` INT            NOT NULL                COMMENT '变动前分值',
    `after_score`  INT            NOT NULL                COMMENT '变动后分值（已截断到 [min,max]）',
    `reason_code` VARCHAR(32)     NOT NULL                COMMENT 'ON_TIME / OVERDUE_LIGHT / OVERDUE_HEAVY / DAMAGE / CANCEL_AFTER_CONFIRM / ADMIN_ADJUST',
    `reason_desc` VARCHAR(255)             DEFAULT NULL   COMMENT '可读说明',
    `created_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_credit_biz` (`user_id`, `order_id`, `reason_code`) COMMENT '幂等键：同一订单同一原因只记一次',
    KEY `idx_credit_user_time` (`user_id`, `created_at`) COMMENT '信誉明细按时间倒序',
    KEY `idx_credit_order` (`order_id`),
    CONSTRAINT `ck_credit_range` CHECK (`before_score` BETWEEN 0 AND 100 AND `after_score` BETWEEN 0 AND 100)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '信誉分变动流水表';

-- ---------------------------------------------------------------------------
-- 12. loss_log 损耗记录表
-- 用途：每次归还算一次 w = 租期系数 × 类目系数 × 归还状态系数，
--       记录本次 w、累计前后的 L、以及触发的动作（保养/检修）。
-- ---------------------------------------------------------------------------
CREATE TABLE `loss_log` (
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `item_id`       BIGINT UNSIGNED NOT NULL               COMMENT '物品',
    `order_id`      BIGINT UNSIGNED NOT NULL               COMMENT '来源订单',
    `term_coef`     DECIMAL(6, 4)   NOT NULL               COMMENT '租期系数（按天数梯度）',
    `category_coef` DECIMAL(6, 4)   NOT NULL               COMMENT '类目系数（来自 category.loss_coef）',
    `return_coef`   DECIMAL(6, 4)   NOT NULL               COMMENT '归还状态系数：完好 1.0 / 轻微磨损 1.5 / 损坏 3.0',
    `weight`        DECIMAL(6, 4)   NOT NULL               COMMENT '本次损耗权重 w = 三者乘积',
    `loss_before`   DECIMAL(6, 4)   NOT NULL DEFAULT 0.0000 COMMENT '累计 L（变动前）',
    `loss_after`    DECIMAL(6, 4)   NOT NULL DEFAULT 0.0000 COMMENT '累计 L（变动后，截断到 [0,1]）',
    `return_state`  VARCHAR(16)     NOT NULL               COMMENT 'INTACT 完好 / WORN 轻微磨损 / DAMAGED 损坏',
    `action`        VARCHAR(16)     NOT NULL DEFAULT 'NONE' COMMENT 'NONE / MAINTAIN 保养提醒 / REPAIR 强制检修',
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
    PRIMARY KEY (`id`),
    KEY `idx_loss_item_time` (`item_id`, `created_at`) COMMENT '物品损耗历史',
    KEY `idx_loss_order` (`order_id`),
    CONSTRAINT `ck_loss_weight` CHECK (`weight` >= 0 AND `loss_before` >= 0 AND `loss_after` >= 0
                                       AND `loss_before` <= 1 AND `loss_after` <= 1),
    CONSTRAINT `ck_loss_state` CHECK (`return_state` IN ('INTACT', 'WORN', 'DAMAGED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '损耗记录表';

-- ===========================================================================
-- 七、纠纷与消息
-- ===========================================================================

-- ---------------------------------------------------------------------------
-- 13. appeal 纠纷申诉表
-- 用途：订单纠纷裁决。状态单向：PENDING → ACCEPTED / REJECTED。
-- ---------------------------------------------------------------------------
CREATE TABLE `appeal` (
    `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_id`     BIGINT UNSIGNED NOT NULL                COMMENT '被申诉订单',
    `applicant_id` BIGINT UNSIGNED NOT NULL                COMMENT '申请人',
    `reason`       VARCHAR(500)    NOT NULL                COMMENT '申诉理由',
    `evidence_urls` VARCHAR(1000)           DEFAULT NULL   COMMENT '证据图片，逗号分隔',
    `status`       VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / ACCEPTED / REJECTED',
    `verdict`      VARCHAR(500)             DEFAULT NULL   COMMENT '裁决结论',
    `verdict_type` VARCHAR(24)              DEFAULT NULL   COMMENT 'RENTER_WIN 支持承租方 / OWNER_WIN 支持出租方 / PARTIAL 部分支持 / REJECTED 驳回（双方都能申诉，方向相反，故用枚举而非布尔）',
    `admin_id`     BIGINT UNSIGNED          DEFAULT NULL   COMMENT '裁决人',
    `verdict_time` DATETIME                 DEFAULT NULL   COMMENT '裁决时间',
    `created_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
    `update_time`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_appeal_status` (`status`, `created_at`) COMMENT '管理端待处理列表',
    KEY `idx_appeal_order` (`order_id`),
    KEY `idx_appeal_applicant` (`applicant_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '纠纷申诉表';

-- ---------------------------------------------------------------------------
-- 14. message 站内消息表
-- 用途：订单状态变更 / 信誉变动 / 检修提醒的通知。不做实时推送（不做 IM）。
-- ---------------------------------------------------------------------------
CREATE TABLE `message` (
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`    BIGINT UNSIGNED NOT NULL                COMMENT '接收人',
    `type`       VARCHAR(16)     NOT NULL                COMMENT 'ORDER 订单 / CREDIT 信誉 / MAINTAIN 检修 / SYSTEM 系统',
    `title`      VARCHAR(64)     NOT NULL                COMMENT '标题',
    `content`    VARCHAR(500)    NOT NULL                COMMENT '正文',
    `biz_type`   VARCHAR(32)              DEFAULT NULL   COMMENT '业务类型（用于前端跳转）',
    `biz_id`     BIGINT UNSIGNED          DEFAULT NULL   COMMENT '业务主键（如 orderId）',
    `is_read`    TINYINT         NOT NULL DEFAULT 0      COMMENT '0 未读 / 1 已读',
    `read_time`  DATETIME                 DEFAULT NULL   COMMENT '阅读时间',
    `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    `deleted`    TINYINT         NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 正常 / 1 已删',
    PRIMARY KEY (`id`),
    KEY `idx_msg_user_read` (`user_id`, `is_read`, `created_at`) COMMENT '未读数 + 消息列表（一个索引覆盖两个查询）'
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '站内消息表';

-- ===========================================================================
-- 八、规则外置（配置表，对应需求总结 §5 的 27 个键）
-- 设计取舍：
--   · 同类同构的参数 → 结构化单行表（credit_policy / fee_policy / loss_policy）
--     好处：类型是 DECIMAL/INT，不会把 0.5 存成字符串再解析；改一个阈值 = UPDATE 一列。
--   · 异构零散参数 → 键值表（sys_config）
--     好处：加参数不用改表结构。
--   · 每张策略表固定 id = 1（单行），PolicyLoader 直接 SELECT ... WHERE id = 1。
-- ===========================================================================

-- ---------------------------------------------------------------------------
-- 15. credit_policy 信誉策略（单行）
-- ---------------------------------------------------------------------------
CREATE TABLE `credit_policy` (
    `id`                          TINYINT       NOT NULL DEFAULT 1    COMMENT '固定为 1，单行表',
    `init_score`                  INT           NOT NULL DEFAULT 80   COMMENT 'credit.init 初始分',
    `min_score`                   INT           NOT NULL DEFAULT 0    COMMENT 'credit.min',
    `max_score`                   INT           NOT NULL DEFAULT 100  COMMENT 'credit.max',
    `reward_on_time`              INT           NOT NULL DEFAULT 2    COMMENT 'credit.reward.on_time 按时归还加分',
    `reward_daily_cap`            INT           NOT NULL DEFAULT 4    COMMENT 'credit.reward.daily_cap 日加分上限',
    `penalty_overdue_light`       INT           NOT NULL DEFAULT -5   COMMENT 'credit.penalty.overdue_light 轻微逾期（≤3 天）每天扣分，负值',
    `penalty_overdue_heavy`       INT           NOT NULL DEFAULT -10  COMMENT 'credit.penalty.overdue_heavy 严重逾期（>3 天）每天扣分，负值',
    `penalty_damage`              INT           NOT NULL DEFAULT -10  COMMENT 'credit.penalty.damage 损坏扣分',
    `penalty_cancel_after_confirm` INT          NOT NULL DEFAULT -3   COMMENT 'credit.penalty.cancel_after_confirm 确认后取消扣分',
    `tier_free_deposit_min`       INT           NOT NULL DEFAULT 95   COMMENT 'credit.tier.free_deposit 免押金门槛（≥）；母版为 80，本项目上调至 95（见需求总结 §14.1）',
    `tier_normal_min`             INT           NOT NULL DEFAULT 60   COMMENT 'credit.tier.normal 正常租用区间下界',
    `tier_normal_max`             INT           NOT NULL DEFAULT 94   COMMENT 'credit.tier.normal 正常租用区间上界；母版为 79，因免押门槛上调而放宽到 94（避免 80-94 空档）',
    `tier_deposit_up_min`         INT           NOT NULL DEFAULT 40   COMMENT 'credit.tier.deposit_up 押金上浮区间下界',
    `tier_deposit_up_max`         INT           NOT NULL DEFAULT 59   COMMENT 'credit.tier.deposit_up 押金上浮区间上界',
    `tier_deposit_up_ratio`       DECIMAL(4, 2) NOT NULL DEFAULT 1.50 COMMENT 'credit.tier.deposit_up 押金上浮系数 ×1.5',
    `tier_suspend_below`          INT           NOT NULL DEFAULT 40   COMMENT 'credit.tier.suspend 低于此值暂停租用资格',
    `update_time`                 DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    CONSTRAINT `ck_credit_policy_score` CHECK (`min_score` <= `init_score` AND `init_score` <= `max_score`),
    CONSTRAINT `ck_credit_policy_ratio` CHECK (`tier_deposit_up_ratio` >= 1)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '信誉策略配置（单行）';

-- ---------------------------------------------------------------------------
-- 16. fee_policy 费用策略（单行）
-- ---------------------------------------------------------------------------
CREATE TABLE `fee_policy` (
    `id`                  TINYINT       NOT NULL DEFAULT 1    COMMENT '固定为 1，单行表',
    `overdue_tier1_days`  INT           NOT NULL DEFAULT 3   COMMENT 'fee.overdue.tier1_days 阶梯一适用天数上限',
    `overdue_tier1_rate`  DECIMAL(6, 4) NOT NULL DEFAULT 0.5000 COMMENT 'fee.overdue.tier1_rate 阶梯一费率（×日租金/天）',
    `overdue_tier2_rate`  DECIMAL(6, 4) NOT NULL DEFAULT 1.0000 COMMENT 'fee.overdue.tier2_rate 阶梯二费率',
    `overdue_rate_min`    DECIMAL(6, 4) NOT NULL DEFAULT 0.1000 COMMENT 'fee.overdue_rate.min 物品逾期费率下界',
    `overdue_rate_max`    DECIMAL(6, 4) NOT NULL DEFAULT 2.0000 COMMENT 'fee.overdue_rate.max 物品逾期费率上界',
    `daily_price_min`     DECIMAL(10, 2) NOT NULL DEFAULT 1.00  COMMENT 'fee.daily_price.min 日租金下界',
    `daily_price_max`     DECIMAL(10, 2) NOT NULL DEFAULT 500.00 COMMENT 'fee.daily_price.max 日租金上界',
    `deposit_ratio_min`   DECIMAL(6, 2) NOT NULL DEFAULT 1.00   COMMENT 'fee.deposit.ratio.min 押金/日租金 下界',
    `deposit_ratio_max`   DECIMAL(6, 2) NOT NULL DEFAULT 50.00  COMMENT 'fee.deposit.ratio.max 押金/日租金 上界',
    `cancel_free_hours`   INT           NOT NULL DEFAULT 24     COMMENT '确认后取消免罚的提前小时数（宽松版）',
    `update_time`         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    CONSTRAINT `ck_fee_policy_rates` CHECK (`overdue_tier1_rate` >= 0 AND `overdue_tier2_rate` >= 0
                                             AND `overdue_tier2_rate` >= `overdue_tier1_rate`),
    CONSTRAINT `ck_fee_policy_bounds` CHECK (`daily_price_min` > 0 AND `daily_price_max` >= `daily_price_min`
                                             AND `overdue_rate_max` >= `overdue_rate_min`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '费用策略配置（单行）';

-- ---------------------------------------------------------------------------
-- 17. loss_policy 损耗策略（单行）
-- ---------------------------------------------------------------------------
CREATE TABLE `loss_policy` (
    `id`                   TINYINT       NOT NULL DEFAULT 1      COMMENT '固定为 1，单行表',
    `threshold_maintain`   DECIMAL(6, 4) NOT NULL DEFAULT 0.6000 COMMENT 'loss.threshold.maintain 保养提醒阈值',
    `threshold_repair`     DECIMAL(6, 4) NOT NULL DEFAULT 0.8500 COMMENT 'loss.threshold.repair 强制检修阈值',
    `term_tier1_max_days`  INT           NOT NULL DEFAULT 3      COMMENT 'loss.coef.term 梯度一：1–3 天',
    `term_tier1_coef`      DECIMAL(6, 4) NOT NULL DEFAULT 0.0100 COMMENT 'loss.coef.term 梯度一系数',
    `term_tier2_max_days`  INT           NOT NULL DEFAULT 7      COMMENT 'loss.coef.term 梯度二：4–7 天',
    `term_tier2_coef`      DECIMAL(6, 4) NOT NULL DEFAULT 0.0200 COMMENT 'loss.coef.term 梯度二系数',
    `term_tier3_coef`      DECIMAL(6, 4) NOT NULL DEFAULT 0.0300 COMMENT 'loss.coef.term 梯度三：>7 天',
    `return_coef_intact`   DECIMAL(6, 4) NOT NULL DEFAULT 1.0000 COMMENT 'loss.coef.return 完好',
    `return_coef_worn`     DECIMAL(6, 4) NOT NULL DEFAULT 1.5000 COMMENT 'loss.coef.return 轻微磨损',
    `return_coef_damaged`  DECIMAL(6, 4) NOT NULL DEFAULT 3.0000 COMMENT 'loss.coef.return 损坏',
    `update_time`          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    CONSTRAINT `ck_loss_policy_threshold` CHECK (`threshold_maintain` > 0 AND `threshold_repair` > `threshold_maintain`
                                                  AND `threshold_repair` <= 1),
    CONSTRAINT `ck_loss_policy_term` CHECK (`term_tier1_max_days` > 0 AND `term_tier2_max_days` > `term_tier1_max_days`),
    CONSTRAINT `ck_loss_policy_return` CHECK (`return_coef_intact` > 0 AND `return_coef_worn` >= `return_coef_intact`
                                               AND `return_coef_damaged` >= `return_coef_worn`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '损耗策略配置（单行）';

-- ---------------------------------------------------------------------------
-- 18. sys_config 系统参数（键值）
-- 用途：放异构零散参数 —— 推荐权重/衰减半衰期/热度窗口/锁 TTL/扫描 cron。
-- ---------------------------------------------------------------------------
CREATE TABLE `sys_config` (
    `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `config_key`   VARCHAR(64)     NOT NULL                COMMENT '配置键，如 recommend.w.cf',
    `config_value` VARCHAR(255)    NOT NULL                COMMENT '配置值，统一按字符串存，按 value_type 解析',
    `value_type`   VARCHAR(16)     NOT NULL DEFAULT 'STRING' COMMENT 'STRING / INT / DECIMAL / CRON',
    `config_group` VARCHAR(32)     NOT NULL DEFAULT 'sys'  COMMENT '分组：recommend / order / sys',
    `description`  VARCHAR(255)             DEFAULT NULL   COMMENT '说明',
    `update_time`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_config_key` (`config_key`),
    KEY `idx_config_group` (`config_group`) COMMENT '按分组整体加载'
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '系统参数表（键值）';

-- ============================================================================
-- 九、钱包与账务（复式记账，K3 的资金侧证据）
-- ============================================================================
DROP TABLE IF EXISTS `account_entry`;
DROP TABLE IF EXISTS `account`;

-- 9.1 账户表（每个用户一行 + 全站一个 EXTERNAL 行）
CREATE TABLE `account` (
    `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `owner_type`     VARCHAR(16)     NOT NULL                COMMENT 'USER 用户 / EXTERNAL 系统外部（银行卡等资金来源去向的镜像）',
    `owner_id`       BIGINT UNSIGNED NOT NULL DEFAULT 0      COMMENT 'USER 时为 user.id；EXTERNAL 固定 0',
    `cash_balance`   DECIMAL(12,2)   NOT NULL DEFAULT 0.00   COMMENT '可用余额（EXTERNAL 允许为负，代表净流入）',
    `frozen_balance` DECIMAL(12,2)   NOT NULL DEFAULT 0.00   COMMENT '冻结余额（在租订单的租金+押金）',
    `version`        INT             NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    `created_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_account` (`owner_type`, `owner_id`),
    CONSTRAINT `ck_account_balances` CHECK (`owner_type` = 'EXTERNAL' OR (`cash_balance` >= 0 AND `frozen_balance` >= 0)),
    CONSTRAINT `ck_account_owner` CHECK ((`owner_type` = 'EXTERNAL' AND `owner_id` = 0) OR (`owner_type` = 'USER' AND `owner_id` > 0)),
    CONSTRAINT `ck_account_type` CHECK (`owner_type` IN ('USER','EXTERNAL'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '账户表（复式记账的账户主体）';

-- 9.2 分录表（只追加，不改写；每笔业务借贷成对）
CREATE TABLE `account_entry` (
    `id`                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `entry_no`           VARCHAR(32)     NOT NULL                COMMENT '分录号 F + yyyyMMdd + 4 位当日序号 + 2 位组内序号',
    `biz_type`           VARCHAR(24)     NOT NULL                COMMENT 'RECHARGE / ORDER_FREEZE / ORDER_RELEASE / SETTLE / VERDICT / ADMIN_ADJUST',
    `biz_id`             BIGINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '关联业务主键：订单 id / 结算单 id / 0 表示无单据',
    `action`             VARCHAR(24)     NOT NULL                COMMENT 'FREEZE / RELEASE / RENT / OVERDUE / LOSS / REFUND / ADJUST / RECHARGE',
    `from_account_id`    BIGINT UNSIGNED NOT NULL                COMMENT '出账账户',
    `to_account_id`      BIGINT UNSIGNED NOT NULL                COMMENT '入账账户',
    `from_bucket`        VARCHAR(8)      NOT NULL DEFAULT 'CASH' COMMENT '出账桶：CASH 可用 / FROZEN 冻结',
    `to_bucket`          VARCHAR(8)      NOT NULL DEFAULT 'CASH' COMMENT '入账桶：CASH 可用 / FROZEN 冻结',
    `amount`             DECIMAL(12,2)   NOT NULL                COMMENT '金额（恒为正，方向由 from/to 表达）',
    `from_balance_after` DECIMAL(12,2)   NOT NULL                COMMENT '出账后该账户该桶余额快照',
    `to_balance_after`   DECIMAL(12,2)   NOT NULL                COMMENT '入账后该账户该桶余额快照',
    `remark`             VARCHAR(255)             DEFAULT NULL   COMMENT '说明',
    `created_at`         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记账时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_entry_no` (`entry_no`),
    UNIQUE KEY `uk_entry_biz` (`biz_type`, `biz_id`, `action`, `from_account_id`, `to_account_id`),
    KEY `idx_entry_from` (`from_account_id`, `created_at`) COMMENT '按账户查流水（出账侧）',
    KEY `idx_entry_to` (`to_account_id`, `created_at`)     COMMENT '按账户查流水（入账侧）',
    CONSTRAINT `ck_entry_amount` CHECK (`amount` > 0),
    CONSTRAINT `ck_entry_accounts` CHECK (`from_account_id` <> `to_account_id` OR `from_bucket` <> `to_bucket`),
    CONSTRAINT `ck_entry_buckets` CHECK (`from_bucket` IN ('CASH','FROZEN') AND `to_bucket` IN ('CASH','FROZEN'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '分录表（复式记账，只追加）';

-- ============================================================================
-- 建表完成：21 张表
-- ============================================================================
