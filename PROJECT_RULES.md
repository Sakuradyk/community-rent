# PROJECT_RULES.md · 项目约定（AI 必读）

> **这个文件是什么**：给 AI 读的**契约文件**。它把「技术栈版本、代码规范、目录结构、禁止事项」一次性钉死，避免每次新会话 AI 凭训练记忆猜错版本、猜错包名、自作主张加功能。
>
> **怎么用**：每次开新会话，第一句话固定是——
>
> ```
> 请先读 PROJECT_RULES.md、社区物品租用平台-需求总结.md、项目进度.md，然后开始本次任务。
> ```
>
> （前两份回答「怎么做」，`项目进度.md` 回答「做到哪了、下一步做什么」——不读它容易重复完成已有功能或跳过未验证的硬指标。）
>
> **效力**：四者冲突时按此顺序裁决——**说明书 > 需求总结 > 本文件 > AI 习惯**。本文件优先级高于 AI 的默认习惯，低于 `社区物品租用平台-需求总结.md`（需求口径）。
>
> **版本**：v1.16 · 2026-11-19 · 配套说明书 385 行版、需求总结 v1.0、`docs/AI协作开发手册.md` v2.0

---

## 0. 铁律 12 条（**先读这一页，其余都是解释**）

> 本文件 600+ 行，长文档越靠后越容易被略读，而最容易出错的 §8、§14 恰在末尾。以下 12 条是**不可跳过**的部分；本节与后文冲突时，以本节为准，并**立即指出矛盾**而不是二选一执行。

1. **K2 三层不许删层**：应用层预检 → Redis 锁 → 唯一索引兜底，缺一层即错；**抢锁失败对外必须报 4090，不许漏成 5003**（§8.3）。
2. **锁在事务外，释放在提交后**：加锁方法**不标** `@Transactional`；解锁必须等事务 `afterCompletion`（§8.3）。
3. **占用必须逐日展开**：跨 3 天的订单在 `order_occupancy` 插 **3 行**，不是 1 行（§8.3）。
4. **金额只用 `BigDecimal` + `DECIMAL(10,2)`**，比较用 `compareTo`，禁止 `double`/`float`（§6）。
5. **一切策略数值来自配置表**（§9 清单），代码里出现字面量即错。
6. **禁用 `javax.*`**，一律 `jakarta.*`；Boot 3.2 / Security 6 / `mybatis-plus-spring-boot3-starter`（§2）。
7. **先方案后代码**：动手前给「要改哪些文件 + 关键决策 + 风险」，等我确认（§13 第 1 条）。
8. **只改点名文件**，不顺手重构、不擅自加依赖（§2.1）。
9. **没跑过的验证不许说「已完成」**：交付必须附命令与真实输出（§14.3）。
10. **三条红线不许踩**：冲突检测不许降级为「前端禁用日期」；结算不许降级为「管理员点按钮」；推荐不许降级为「按时间倒序」（§1）。
11. **改表结构必须先说**，并同步 `docs/数据库设计.md` 与 `backend/sql/`。
12. **不确定就问**，禁止编造类名、方法名、配置项、版本号。

---

## 1. 项目一句话

**基于 Spring Boot 3.2 的社区物品租用平台**：按天计费、按时段互斥的社区/校园闲置物品租用系统。

- **三条硬指标**（验收核心）：同一时段零放行 · 结算零人工 · 推荐结果可解释
- **六大模块**：M1 发布租用 / M2 冲突检测 / M3 费用结算 / M4 信誉分 / M5 推荐 / M6 损耗检修
- **两端**：用户端（Vue 3）+ 管理端（Vue 3 + Element Plus 表格表单），共用一套 REST API
- **六个技术关卡**：K1 双边角色建模 / K2 并发时段互斥 / K3 幂等结算 / K4 信誉量化 / K5 冷启动推荐 / K6 损耗量化

> ⚠️ AI 注意：K2、K3 是本课题的立论核心。任何触及订单写入与费用计算的改动，都必须按本文件 §8、§9 的规范执行，**不接受简化方案**（例如「前端把日期禁掉就不会冲突了」「后台加个按钮手动结算」）。

---

## 2. 技术栈与版本锁定

> **规则**：下表版本为准。AI 给出的代码若使用了其他版本的 API，视为错误，必须重写。

| 层次 | 组件 | **锁定版本** | 常见错误写法（禁止） |
| :-- | :-- | :-- | :-- |
| 语言 | JDK | **17**（本机 17.0.0 LTS） | 用 21 的 preview 特性、用 8 的写法绕开新语法 |
| 框架 | Spring Boot | **3.2.x** | 2.7 的 `WebSecurityConfigurerAdapter` |
| 命名空间 | Jakarta EE | **`jakarta.*`** | **`javax.servlet.*` / `javax.persistence.*`（Boot 3 已废弃）** |
| ORM | MyBatis-Plus | **3.5.7**（boot3 starter，已实测） | ①`mybatis-plus-boot-starter`（Boot 2 专用）；②盲升 3.5.9+ —— 自 3.5.9 起 jsqlparser 拆为独立模块 `mybatis-plus-jsqlparser`，**不额外引入则分页插件启动即抛错** |
| 数据库 | MySQL | **8.0.39**（InnoDB） | 5.7 语法、`utf8` 而非 `utf8mb4` |
| 缓存 | Redis | **5.0.14.1**（本机 Windows 服务，`tporadowski` 移植版，已实测 `redis_version:5.0.14.1`） | ①写「7.x」—— 本机没有 7，Docker 也未装；②使用 Redis 6/7 才有的命令（`ACL`、`ZADD GT/LT`、`LPOS`、`SINTERCARD`、`CLIENT NO-EVICT`），本机 5.0 直接报 unknown command |
| 安全 | Spring Security | **6.x** | `extends WebSecurityConfigurerAdapter` |
| 令牌 | jjwt | **0.12.x** | 0.9 的 `Jwts.parser().setSigningKey()` |
| 连接池 | **Druid** | **1.2.23**，坐标 `com.alibaba:druid-spring-boot-3-starter` | `druid-spring-boot-starter`（Boot 2 专用，Boot 3 启动即报 `AutoConfiguration.imports` 缺失） |
| 构建 | Maven | **3.9.9** | Gradle（本项目不用） |
| 前端框架 | Vue | **3.5.x**（实测已装 3.5.45） | Vue 2 的 `data(){return{}}` + `this.xxx` |
| 前端构建 | Vite | **5.x**（5 线最后一版 5.4.21，`npm view vite@5 version` 核对） | vue-cli / webpack |
| Vue 插件 | @vitejs/plugin-vue | **5.2.x** | `@vitejs/plugin-vue2` |
| UI 库 | Element Plus | **2.5+**（实测 2.14.8） | Element UI（Vue 2 专用） |
| 状态管理 | Pinia | **2.x**（实测 2.3.1） | Vuex |
| 路由 | vue-router | **4.x**（实测 4.6.5） | vue-router 3 |
| 请求库 | Axios | 1.x（实测 1.21.1） | fetch 直调（除特殊场景） |
| 图表 | ECharts | 5.x | — |
| 日期库 | dayjs | `^1.11.0`（§4.6 F8 点名） | 用 `new Date()` 手算日期、引入 moment（体积约 4 倍） |
| 样式预处理 | sass | `^1.77.0`，**包名 `sass`**（§4.6 F12 点名） | `node-sass`（已停止维护，Node 24 编译不过） |
| 组件按需引入 | unplugin-vue-components | `^0.27.0`（§4.6 F5 点名） | 全局 `app.use(ElementPlus)`、`unplugin-element-plus`（旧方案，只处理样式不处理 API） |
| 自动导入 | unplugin-auto-import | **`^0.19.0`**（该包 0.x 线最后一版，**不存在 0.27.x**；1.x 起改 API，未验证不升） | 写 `^0.27.0`（装不上，`No matching version found`） |
| 运行时 | Node.js | **v24.10.0**（本机） | — |

### 2.1 依赖引入规则

- **不得擅自新增依赖**。需要新依赖时，先在回复里说明「为什么现有依赖做不到、体积多大、是否有安全风险」，等我确认。
- 每个新增依赖必须写进本节表格。
- 禁止引入：Dubbo、Nacos、Kafka、RocketMQ、Elasticsearch、ShardingSphere、XXL-Job、Sa-Token、JPA/Hibernate、**spring-cloud 任何组件**（含 `spring-cloud-context` —— 所以 `@RefreshScope` 在本项目**不可用**）、MapStruct、Lombok 之外的其他注解处理器。
- **已获批的例外（无需再问）**：§4.6 点名的 `dayjs`、`sass`、`unplugin-auto-import`、`unplugin-vue-components` 已列入 §2 表格，可直接写进 `package.json`。除此之外前端也一律不引新包：**假数据写 `src/mock/*.js` 纯 JS 对象，不引 mockjs / faker**。
- 版本写法统一为 `^主.次`，实际锁定的具体版本在 `pnpm install` 后回填本节（以 `package.json` 为准）。

---

## 3. 本机开发环境（实测，勿猜）

| 项 | 实测值 | 备注 |
| :-- | :-- | :-- |
| 操作系统 | Windows | 命令用 PowerShell |
| 工作目录 | `C:\Users\duyikang\Desktop\社区物品租用平台` | 项目根 |
| `JAVA_HOME` | `C:\Program Files\Java\jdk-17` | 已配置 |
| JDK | `17` (2021-09-14 LTS) | `java -version` 可用 |
| Maven | **3.9.9 已安装** → `C:\dev\apache-maven-3.9.9`（`MAVEN_HOME` 已设，用户 `Path` 已追加 `bin`） | 已实测 `mvn -v` 通过；**只在安装后新开的终端里生效**，旧窗口需重启 |
| Maven 镜像 | `C:\Users\duyikang\.m2\settings.xml`：`aliyun-public` → `https://maven.aliyun.com/repository/public` | 已实测下载正常（~120 kB/s）；中央仓库直连慢，**不要删这条 mirror** |
| Maven 控制台编码 | `platform encoding: GBK` | 中文日志可能乱码；需要时 `chcp 65001` 后再执行，pom 里必须设 `UTF-8` |
| MySQL | **8.0.39**，服务名 `MySQL80`，端口 3306 | 已运行 |
| MySQL 客户端 | `E:\MySQL\MySQL Server 8.0\bin\mysql.exe` | 不在 PATH |
| MySQL 账号 | `root` / `123456`（仅本机开发） | — |
| Redis | 服务名 `Redis`，`127.0.0.1:6379`，**版本 5.0.14.1**（已实测 `INFO server`），安装目录 `C:\Users\duyikang\Doubao\chats\2026-07-22\new-chat\Redis\` | 已运行；`redis-cli.exe` 与 `redis-server.exe` 都在该目录，**不在 PATH**。是 tporadowski 的 Windows 移植版，非官方 7.x，**没有 ACL / `ZADD GT` 等 6.x+ 命令** |
| Redis 密码 | `123456`（`requirepass`） | **必须配 `spring.data.redis.password`，否则连接报 NOAUTH** |
| Node.js | `v24.10.0` | — |
| 包管理器 | `pnpm`（优先）、`npm` | 前端统一用 pnpm |
| Git | `2.56.0` | — |
| Docker | **未安装** | 不要生成依赖 docker 的部署方案 |

> **AI 注意**：涉及数据库、Redis 连接时直接使用上表参数，不要写 `yourpassword` 之类的占位符再让我填。

---

## 4. 工程结构

### 4.1 后端：**单模块 Maven 项目**（不做多模块）

> 对 `需求总结 §11.2 W4`「Maven 多模块」的**简化**，理由：单人开发没有模块边界收益，多模块只会增加构建复杂度与 AI 出错面。改以**包分层**表达四层架构。

```
社区物品租用平台/
├── PROJECT_RULES.md              ← 本文件（硬约束，每个会话必读）
├── 项目进度.md                    ← 做到哪一步、下一步做什么（每个会话必读）
├── 社区物品租用平台-需求总结.md     ← FR 与边界裁定
├── 社区物品租用平台-课题说明书.md   ← 立论母版，仅在写论文/答辩时读
├── .gitignore
├── README.md                     ← 启动说明
├── backend/                      ← Spring Boot 3.2
│   ├── pom.xml
│   ├── sql/                      ← DDL、初始化数据、结构自检
│   │   ├── 01-schema.sql
│   │   ├── 02-data.sql
│   │   └── 03-verify.sql
│   ├── src/main/java/com/duyikang/rental/
│   │   ├── RentalApplication.java
│   │   ├── common/               ← 公共层（无业务）
│   │   │   ├── result/           ← Result, PageResult
│   │   │   ├── exception/        ← BizException, GlobalExceptionHandler
│   │   │   ├── constant/         ← ErrorCode 及各枚举
│   │   │   ├── util/             ← FileStorageUtil, DateRangeUtil, FeeCalculatorUtil
│   │   │   ├── lock/             ← RedisLock（SET NX PX + Lua 比对删除，K2 第二层）
│   │   │   └── config/           ← TraceIdFilter, MybatisPlusConfig, JacksonConfig, WebMvcConfig, FileStorageProperties
│   │   ├── security/             ← SecurityConfig, JwtUtil, JwtAuthenticationFilter, LoginUser
│   │   ├── module/
│   │   │   ├── user/             ← 用户与权限（controller/service/mapper/entity/dto/vo）
│   │   │   ├── item/             ← 物品
│   │   │   ├── order/            ← 订单与冲突检测（K2）：OrderController / OrderService+Impl（加锁，无事务）/ OrderTxService+Impl（事务落库）/ OrderStateService+Impl（状态流转，CAS + 事务）
│   │   │   ├── settlement/       ← 费用结算（K3）
│   │   │   ├── credit/           ← 信誉分（K4）
│   │   │   ├── recommend/        ← 推荐（K5）
│   │   │   ├── loss/             ← 损耗检修（K6）
│   │   │   ├── appeal/           ← 申诉与纠纷
│   │   │   ├── message/          ← 站内信
│   │   │   └── system/           ← 系统支撑：配置参数（sys_config）与文件存储（上传/静态映射）
│   │   ├── admin/                ← 管理端专用接口（controller 层）：AdminItemController / AdminOrderController（scan-overdue）
│   │   └── job/                  ← 定时任务：OrderScanJob（cron 从 sys_config 读，且必须配管理端手动触发）
│   │
│   └── src/main/resources/       ← 注意：与 src/main/java 同级，不在 Java 包目录下
│       ├── application.yml
│       ├── application-dev.yml
│       └── mapper/               ← MyBatis XML（复杂 SQL 才用 XML：ItemMapper.xml 列表三档排序 / OrderOccupancyMapper.xml 占用查询）
├── web-user/                     ← 用户端 Vue 3 + Vite
├── web-admin/                    ← 管理端 Vue 3 + Vite + Element Plus
└── docs/
    ├── 数据库设计.md
    ├── 接口文档.md
    ├── 测试用例.md
    ├── AI协作开发手册.md          ← 方法论：怎么和 AI 配合（不含本文件的规则副本）
    └── notes/                    ← 每个 K 点的技术笔记（论文素材）
```

### 4.2 包内分层（每个 module 内部结构固定）

```
module/order/
├── controller/OrderController.java
├── service/OrderService.java + impl/OrderServiceImpl.java
├── mapper/OrderMapper.java
├── entity/Order.java  OrderOccupancy.java
├── dto/              ← 入参（XxxCreateDTO / XxxQueryDTO），带校验注解
├── vo/               ← 出参（XxxVO），不直接返回 Entity
└── convert/          ← Entity ↔ DTO/VO 转换（手写，不引 MapStruct）
```

### 4.3 分层职责（硬性）

| 层 | 只做 | **禁止** |
| :-- | :-- | :-- |
| Controller | 参数校验（`@Valid`）、调用 Service、封装 `Result` | 写业务逻辑、直接调 Mapper、拼 SQL |
| Service | 业务规则、事务边界、调用多个 Mapper、发消息 | 返回 `Result`、处理 `HttpServletRequest` |
| Mapper | 数据访问 | 写业务判断 |
| Entity | 表映射 | 承载业务方法、直接返回给前端 |
| DTO/VO | 传输结构 | 暴露 Entity 内部字段（如 `password_hash`） |

**禁止跨层调用**：Controller 不得直接调 Mapper；Service 不得调 Controller。

### 4.4 前端：两个独立 Vite 应用（**不做 Monorepo**）

`web-user`（用户端）与 `web-admin`（管理端）是两个**互相独立**的工程：各自的 `package.json`、各自 `npm install`、各自构建、各自部署。

> **为什么不合并**：两端页面形态、组件用法、构建产物完全不同（用户端是移动优先的卡片流，管理端是表格+表单的后台）；单人毕设引入 monorepo/pnpm workspace 只会让「装依赖→构建→排查」多一层出错面，收益为零。
>
> **共用的是接口契约（同一套 REST API），不是代码。** 少量重复（`constants.js`、`format.js`、`request.js`）**刻意接受**，不做跨工程抽包——抽包的代价是构建链路变复杂，而这两个文件加起来不到 300 行。

#### web-user（用户端）

```
web-user/
├── index.html
├── package.json
├── vite.config.js                 # @ 别名 + /api 代理到 8080
├── .env.development               # VITE_API_BASE=/api
└── src/
    ├── main.js                    # 挂载 router / pinia / Element Plus
    ├── App.vue
    ├── api/                       # 与后端模块一一对应，一函数一接口
    │   ├── request.js             # ★ axios 单例 + 请求/响应拦截器（唯一出口）
    │   ├── auth.js  item.js  order.js
    │   └── credit.js  recommend.js  message.js
    ├── router/
    │   ├── index.js               # 路由表（全部懒加载）
    │   └── guard.js               # 登录态前置守卫
    ├── stores/                    # Pinia，只放跨页面状态
    │   ├── user.js                # token、当前用户、信誉分
    │   ├── order.js               # 下单草稿（详情页 → 确认页）
    │   └── app.js                 # 全局 loading、未读消息数
    ├── views/
    │   ├── home/Home.vue                     # 首页 + 推荐位
    │   ├── item/{ItemList,ItemDetail,ItemPublish,MyItems}.vue
    │   ├── order/{OrderCreate,OrderList,OrderDetail}.vue
    │   ├── mine/{Credit,Messages,Profile}.vue
    │   └── auth/{Login,Register}.vue
    ├── components/
    │   ├── ItemCard.vue           # 物品卡片
    │   ├── OccupancyCalendar.vue  # ★ 占用日历（K2 的前端表达）
    │   ├── PriceQuote.vue         # ★ 费用试算（只渲染后端 quote 结果）
    │   ├── StatusTag.vue          # 订单状态标签（文案/颜色来自 constants）
    │   └── EmptyState.vue
    ├── layout/
    │   ├── DefaultLayout.vue
    │   └── Header.vue  Footer.vue
    ├── utils/
    │   ├── constants.js           # ★ 枚举唯一来源，与后端枚举严格对齐
    │   ├── format.js              # 金额/日期格式化
    │   └── validate.js
    └── assets/styles/{variables.scss,global.scss}
```

#### web-admin（管理端）

与 web-user 同构，**差异只在下面这些**（其余 `api/request.js`、`utils/format.js` 照抄后改基地址）：

```
web-admin/
└── src/
    ├── api/          # 同名结构 + audit.js  config.js  appeal.js  dashboard.js
    ├── router/       # 含角色守卫：非 ADMIN 直接踢回登录页
    ├── stores/user.js
    ├── views/
    │   ├── auth/Login.vue
    │   ├── audit/AuditList.vue        # 物品发布审核
    │   ├── config/ConfigPanel.vue     # 费率上下限 / 信誉策略 / 损耗阈值
    │   ├── appeal/AppealList.vue      # 纠纷裁决
    │   ├── dashboard/Dashboard.vue    # 数据看板（ECharts）
    │   └── users/UserList.vue
    ├── components/{ConfigTable,AuditDrawer,StatCard}.vue
    ├── layout/{AdminLayout,Sidebar}.vue
    └── utils/{constants,format}.js
```

管理端必须单独有一个**「立即执行逾期扫描」按钮**（需求总结 §9「可测试时间」），挂在 `Dashboard.vue` 或 `ConfigPanel.vue` 上——答辩现场靠它把 OVERDUE 状态跑出来。

### 4.5 前端分层职责（硬性）

| 层 | 目录 | 只做 | **禁止** |
| :-- | :-- | :-- | :-- |
| 页面层 | `views/` | 组装组件、调 store 与 api、处理用户交互、路由跳转 | 直接写 `axios(...)`、手拼 URL、**算钱、算冲突** |
| 组件层 | `components/` | 纯展示、接 `props`、`emit` 事件 | 直接调 api、直接改 store、写业务规则 |
| 数据层 | `api/` + `stores/` | 接口封装（一函数一接口）、跨页面共享状态 | 在 store 里做 `router.push`、在 api 里做数据加工 |
| 基础层 | `utils/` `constants/` `assets/` | 枚举常量、格式化、样式变量 | 任何业务逻辑 |

与后端四层（Controller/Service/Mapper/Entity）是**同构的**：页面层↔Controller、组件层↔VO、数据层↔Service/Mapper、基础层↔common。论文里可直接对比着写。

### 4.6 前端硬性规则（F1–F16）

| 编号 | 规则 | 违反后果 |
| :-- | :-- | :-- |
| **F1** | **前端不参与算钱、不参与判冲突。** 费用一律调 `POST /api/orders/quote`（后端 K3 公式）；占用一律读 `GET /api/items/{id}/calendar` | 前后端两套公式 → 显示价与结算价对不上，答辩必被问倒 |
| **F2** | 枚举**单一来源** `utils/constants.js`，与后端枚举一一对应；页面里禁止出现 `'PENDING'`、`'CONFIRMED'` 字面量 | 后端改枚举值，前端静默错乱 |
| **F3** | **前端权限只做体验，不做安全**：隐藏按钮≠接口安全。所有越权判定必须由后端 `@PreAuthorize` + Service 校验兜底 | 改个 URL 就能看别人订单 |
| **F4** | 统一 `<script setup>` 组合式 API，**禁止 Options API**（`data(){return{}}`、`this.xxx`） | 与 Vue 3 生态割裂，AI 抄 Vue 2 代码 |
| **F5** | Element Plus **按需引入**：`unplugin-auto-import` + `unplugin-vue-components`，不全局 `app.use(ElementPlus)`。**函数式 API（`ElMessage` / `ElMessageBox` / `ElNotification`）不在模板里，按需插件不会替它们注入样式**——显式 `import { ElMessage } from 'element-plus'` 拿到的只是 JS 函数，必须在 `src/main.js` 手工引入 `element-plus/theme-chalk/el-message.css`（及 `el-message-box.css`），否则消息渲染在文档流末尾、用户看不见但接口确实返回了错误码 | 首屏体积翻倍；或"后端报错了前端没反应"，看着像接口 bug 其实是样式没进来 |
| **F6** | 路由**全部懒加载**：`component: () => import('@/views/...')` | 打包成单文件，首屏 3MB+ |
| **F7** | axios **只有一个实例**（`api/request.js`），拦截器统一处理：`code!==0` 抛错+提示、`401` 清 token 跳登录、`4090` 展示候选时段并高亮日历。页面里不重复写 try-catch 弹 toast | 几十处重复错误处理，错误码无人统一 |
| **F8** | 日期用 `dayjs`，传给后端一律 `'YYYY-MM-DD'` 字符串；金额只读展示，固定两位小数 | 传 `Date` 对象 → 时区错一天；金额被 JS 浮点污染 |
| **F9** | **Long 型 id 一律按字符串处理**（后端已 `ToStringSerializer`），禁止 `Number(id)`、禁止 `==` 比较 id | 超过 2^53 精度丢失，订单查不到 |
| **F10** | Pinia **只放跨页面状态**（登录用户、下单草稿、全局 UI）；页面内部状态用 `ref`/`reactive` | store 变成全局垃圾桶，刷新即错乱 |
| **F11** | 开发期用 `vite.config.js` 的 proxy 把 `/api` 转发到 `http://localhost:8080`，**前端代码里不出现后端域名/IP** | 换环境就要改代码 |
| **F12** | 样式用 `scss` + CSS 变量（`variables.scss`），禁止用内联 `style` 堆业务样式（动态计算除外） | 主题无法统一，改色改一天 |
| **F13** | 要「假数据」只能放 `src/mock/*.js` 并在提交说明里标注**哪几个接口还是 mock**；不许把假数据写进 views 假装功能已完成 | 答辩演示时数据全是假的 |
| **F14** | 前端同样受 §13 AI 准则约束：先说方案再写文件、一次只改点名文件、不新增依赖、不写 TODO 当完成 | — |
| **F15** | **凭据只允许「混淆存本地」，且必须可一键清除**：记住密码等本地保存的密码只能经 `utils/remember.js` 做 base64 混淆后进 `localStorage`，注释里必须写明「非加密」；密码/令牌**禁止进日志、URL query、`sessionStorage` 之外的第三方上报**，取消勾选或改密后必须清除 | 明文密码出现在 DevTools/日志/截图里；换浏览器或共用电脑时密码泄漏 |
| **F16** | **字典是数据，枚举是契约**：类目、社区这类会随管理端增删的「字典」只能通过公开字典接口拉（`/api/categories`、`/api/communities`），禁止在前端写死类目名/小区名，也禁止把它们塞进 `constants.js` 冒充枚举；`constants.js` 只放**状态机取值**这类必须与后端逐字一致的契约 | 管理端加了一个类目，前端下拉里没有；或前端硬编码的名字与库里改名后不一致 |

### 4.7 页面清单（= 开发排期依据）

**用户端 web-user**

| 页面 | 路由 | 对应需求 | 优先级 |
| :-- | :-- | :-- | :-- |
| 登录 / 注册 | `/login` `/register` | FR-M1-01 | P0 |
| 忘记密码（知识校验式重置） | `/forgot` | FR-M1-01 | P0 |
| 首页（含推荐位） | `/` | FR-M5-01~04 | P1 |
| 物品列表 / 搜索 | `/items` | FR-M1-03 | P0 |
| 物品详情（占用日历+试算） | `/items/:id` | FR-M1-04、FR-M2-01 | P0 |
| 下单确认页 | `/orders/create?itemId=` | FR-M1-06、FR-M3-01 | P0 |
| 我的订单（我租的/我租出的 Tab） | `/orders` | FR-M1-07 | P0 |
| 订单详情（状态流转+结算单+申诉） | `/orders/:id` | FR-M1-08、FR-M3-05、FR-M6-04 | P0 |
| 发布物品 | `/items/publish` | FR-M1-02 | P0 |
| 我的物品 | `/items/mine` | FR-M1-09 | P0 |
| 我的信誉分 | `/credit` | FR-M4-01 | P0 |
| 消息中心 | `/messages` | FR-M1-10 | P1 |
| 个人中心 | `/mine` | FR-M1-01 | P1 |

> **已交付（截至 2026-11-18）**：登录/注册、忘记密码、首页（物品卡片流 + 类目入口）、物品列表、物品详情（含占用日历）、发布物品（多图）、我的物品、个人中心。**未交付**：下单确认页、我的订单、订单详情、我的信誉分、消息中心、首页推荐位（切片 3–6）。路由与上表必须一致，改路由就得改这一行。

**管理端 web-admin**

| 页面 | 路由 | 对应需求 | 优先级 |
| :-- | :-- | :-- | :-- |
| 登录 | `/login` | — | P0 |
| 物品审核 | `/audit` | FR-M1-11 | P0 |
| 策略配置（费率/信誉/损耗） | `/config` | FR-M3-02、FR-M4-03、FR-M6-03 | P1 |
| 纠纷裁决 | `/appeals` | FR-M6-05 | P1 |
| 数据看板（含逾期扫描按钮） | `/dashboard` | FR-M2-05、FR-M3-06 | P1 |
| 用户管理 | `/users` | — | P2 |

> **已交付（截至 2026-11-18）**：登录、物品审核（列表默认只看待审核，通过即上架、驳回必须写原因）。**未交付**：策略配置、纠纷裁决、数据看板（含「立即执行逾期扫描」按钮）、用户管理。

### 4.8 数据源：Druid 配置契约

**依赖（唯一写法）**

```xml
<dependency>
    <groupId>com.alibaba</groupId>
    <artifactId>druid-spring-boot-3-starter</artifactId>
    <version>1.2.23</version>
</dependency>
```

- 必须用 `-spring-boot-3-starter`（Boot 3 用 `AutoConfiguration.imports` 装配）。
- 若中央仓库拉不到 1.2.23，取 1.2.x 中 `spring-boot-3-starter` 的最高可用版本；**不得**退回 `druid-spring-boot-starter`。
- 不写 `DruidConfig` 手配 `DruidDataSource` bean，不写 `ServletRegistrationBean`——starter 自动装配即可。确实要扩展时才在 `common/config/DruidConfig.java` 里加，且需先说明理由。

**配置（`application-dev.yml`，库名固定 `rental`）**

```yaml
spring:
  datasource:
    type: com.alibaba.druid.pool.DruidDataSource
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/rental?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    username: root
    password: 123456
    druid:
      initial-size: 5
      min-idle: 5
      max-active: 20
      max-wait: 60000
      validation-query: SELECT 1
      test-while-idle: true
      test-on-borrow: false
      test-on-return: false
      time-between-eviction-runs-millis: 60000
      min-evictable-idle-time-millis: 300000
      filters: stat,slf4j
      stat-view-servlet:
        enabled: true
        url-pattern: /druid/*
        login-username: admin
        login-password: druid123
        allow: 127.0.0.1
      web-stat-filter:
        enabled: true
        url-pattern: /*
        exclusions: "*.js,*.css,/druid/*"
```

**硬规则**

| # | 规则 | 原因 |
| :-- | :-- | :-- |
| D1 | 库名固定 `rental`，与包名 `com.duyikang.rental` 一致 | 避免各处写法不一 |
| D2 | `max-active` ≤ 20 | 本机 MySQL `max_connections` 默认 151，单人项目 20 足够，调大只会掩盖连接泄漏 |
| D3 | 必须配 `validation-query: SELECT 1` + `test-while-idle: true` | 否则 MySQL 8 小时空闲断连后首个请求报 `Communications link failure` |
| D4 | `url` 必带 `serverTimezone=Asia/Shanghai` 与 `allowPublicKeyRetrieval=true` | 少一个就报时区错 / `Public Key Retrieval is not allowed` |
| D5 | 监控页 `/druid/*` **必须设账号密码**，`allow` 限 `127.0.0.1`；对外部署时 `enabled: false` | 空口令监控页 = SQL 与表结构全公开 |
| D6 | `filters` 里**先不加 `wall`**；只有明确需要 SQL 防火墙演示时才加，且必须验证多表 `UPDATE ... JOIN` 不被误拦 | `wall` 误报会直接让业务 SQL 报错，排查成本高 |
| D7 | 禁止出现 `spring.datasource.hikari.*` 配置项 | 两套连接池键混写，Druid 生效但 Hikari 键残留成死配置 |
| D8 | 禁止 `new DruidDataSource()`、禁止在代码里 `setPassword("123456")` | 连接池实例必须由容器管理；密码只出现在 yml |
| D9 | `SecurityFilterChain` 里必须放行 `/druid/**`（或至少 `permitAll` 该路径），否则监控页被 Spring Security 401 拦死 | Boot 3 + Security 6 默认全拦截，Druid 监控页打不开是最常见的「配了没用」现象 |
| D10 | URL 里写 `characterEncoding=UTF-8`，**不要写 `utf8` 或 `utf8mb4`** | 该参数是 **Java 字符集名**，不是 MySQL 字符集名。Connector/J 8 会把 `UTF-8` 正确映射为库端的 `utf8mb4`；写 `utf8` 虽能跑但语义模糊，写 `utf8mb4` 属非法 Java 名称、可能启动即 `UnsupportedEncodingException`。库表字符集由 `CREATE DATABASE ... utf8mb4` 保证（已建） |

> **用 Druid 的理由（论文/答辩口径）**：项目负责人指定；相较内置 HikariCP，Druid 自带 **SQL 监控、慢 SQL 统计、连接池实时状态** 与 Web 关联统计，可在答辩时直接打开 `/druid` 页面演示「并发下单时的连接占用与慢 SQL」，作为 K2 并发压测的辅助证据。

---

### 4.9 前端视觉基线（暖橙主题，2026-11-17 与负责人确认）

**风格定档**：温暖亲切、生活化社区感；主色暖橙、辅色木色；细节密度**宽松舒适**（主容器 12px 圆角、留白偏大、列表一屏 3–4 张卡）。登录/注册/找回密码三页共用 `web-user/src/layout/AuthLayout.vue`（左品牌区 + 右表单卡，窄屏 ≤900px 上下堆叠）。

**唯一取值来源**

| 类别 | 文件 | 说明 |
| :-- | :-- | :-- |
| 颜色/字号/间距/圆角/阴影 | `web-user/src/assets/styles/variables.scss`、`web-admin/src/assets/styles/variables.scss` | 页面**禁止写死**色值与像素，只能 `@use ... as *` 后取变量 |
| Element Plus 主题 | 两端 `global.scss` 的 `:root` 覆盖 | 覆盖 `--el-color-primary` 时必须同时覆盖 `-light-3/5/7/8/9` 与 `-dark-2`，否则 hover/disabled 态退回默认蓝 |
| 六状态颜色 | `web-user/src/utils/constants.js` 的 `color` 字段 | 与两端 `$color-*` 状态色逐字一致；页面用 `statusOf(dict, value).color`，**禁止** `<el-tag type="success">` 这类魔法值 |
| 页面版式样板 | `web-user/src/layout/AuthLayout.vue`、`web-user/src/layout/DefaultLayout.vue` | 新页面照抄结构与间距节奏，末期只换皮不动骨架 |

**硬规则**

1. **主色用法分层**：`$color-primary`(#ea580c) 只做交互填充（按钮、选中块，白字对比 3.6:1）；`$color-accent`(#f97316) 只做图形（图标、进度条、装饰），**不得承载小字**；白底文字用 `$color-primary-deep`(#c2410c，对比 5.2:1)。
2. **字号只有五档**（12/14/16/20/28），**间距只有七档**（4/8/12/16/24/32/48）。需要"稍微大一点"就换档，不许出现 `13px`、`18px`。
3. **状态色只从字典取**（同 F2）。新增状态必须同时改后端枚举、`constants.js`、两端 `variables.scss`、`docs/接口文档.md`。
4. **函数式组件样式**按 F5 在 `main.js` 显式引入；改主题后必须 `npm run build` 并 grep `dist/assets/*.css` 确认变量已进包（自查法：`--el-color-primary` 是否命中）。
5. **不做**：暗色模式、插画素材库、动效库、自定义图标字体（§2.1 禁新依赖，装饰一律用纯 CSS 渐变/光斑）。
6. **时间盒**：视觉打磨只在切片收尾时做（≤1 天/次），**不得**挤占 K2/K3 等硬指标模块的开发时间。

## 5. 命名规范

| 对象 | 规则 | 示例 |
| :-- | :-- | :-- |
| 包 | 全小写 | `module.settlement` |
| 类 | UpperCamel | `OrderServiceImpl` |
| 方法/变量 | lowerCamel | `createOrder`、`dailyPrice` |
| 常量 | UPPER_SNAKE | `MAX_PAGE_SIZE` |
| 数据库表 | lower_snake，**单数** | `user`、`item`、`order_occupancy` |
| 数据库字段 | lower_snake | `daily_price`、`credit_score` |
| 索引 | `idx_表_字段` | `idx_order_item_status` |
| 唯一索引 | `uk_表_字段` | `uk_occupancy_item_date` |
| 外键（逻辑外键，不建物理 FK） | `fk_表_被引表` | 仅注释标注，不建约束 |
| REST 路径 | 小写中划线，资源复数 | `/api/items`、`/api/orders/{id}/confirm` |
| 布尔字段 | `is_` 前缀（DB）/ `isXxx`（Java） | `is_read` / `isRead` |
| 枚举 | 全大写 | `OrderStatus.PENDING` |

---

## 6. 数据与类型规范

### 6.1 金额（**最高优先级**）

- Java 一律 `BigDecimal`，**禁止 `double` / `float` / `long` 存分**。
- 数据库一律 `DECIMAL(10,2)`。
- 除法必须指定精度与舍入：`divide(x, 2, RoundingMode.HALF_UP)`。
- `BigDecimal` 比较用 `compareTo() == 0`，**禁止 `equals()`**（会因 scale 不同返回 false）。
- 损耗权重 `w` 用 `DECIMAL(6,4)`，累计 `L` 保留 4 位后截断到 `[0,1]`。

### 6.2 时间

- 日期：`java.time.LocalDate` + `yyyy-MM-dd`。时间：`LocalDateTime` + `yyyy-MM-dd HH:mm:ss`。
- **禁止** `java.util.Date`、`java.sql.Timestamp`。
- 时区固定 `Asia/Shanghai`，在 `application.yml` 里配 `spring.jackson.time-zone`。
- 租期占用一律**半开区间** `[start_date, end_date)`，归还日不计占用。

### 6.3 主键与通用字段

- 主键 `id BIGINT AUTO_INCREMENT`（不做雪花 ID，单人项目无分库需求）。
- `Long` 型 id 返回前端必须转字符串防精度丢失：`@JsonSerialize(using = ToStringSerializer.class)`。
- 所有表带 `created_at`、`updated_at`；逻辑删除用 `deleted TINYINT DEFAULT 0` + MyBatis-Plus `@TableLogic`。

### 6.4 字符集

- 建库建表统一 `DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci`（已执行：`CREATE DATABASE rental ... utf8mb4`）。
- **两种「utf8」别混**：SQL 里写字符集名 → `utf8mb4`；JDBC URL 的 `characterEncoding` → `UTF-8`（Java 字符集名，详见 D10）。两处不一致不报错，但排查时极易误判。

### 6.5 社区（`community_id`）

- 社区统一存 `community_id BIGINT`，指向 `community` 字典；**禁止**再出现 `community VARCHAR` 形式的社区名。
- `user.community_id` **可空**（不填 = 不参与"同小区优先"）；`item.community_id` **必填**。
- "就近"一律用整数等值分档：`同小区 > 同片区 > 其他`，**不做距离计算、不引地图**（需求总结 §10 边界）。
- 排序开关 `community.priority.enabled`（`sys_config`，默认 1）：设为 0 时必须退回原排序，这是论文消融实验的入口，**不许删掉这个分支**。
- 关联完整性由 `A18`（物品必须指向真实社区）/ `A19`（用户非空时必须存在）两条断言巡检，不建物理外键。

---

## 7. 接口与响应规范

### 7.1 统一响应体

```java
Result<T> { int code; String message; T data; String traceId; }
```

- 成功固定 `code = 0`，`message = "成功"`（取自 `ErrorCode.SUCCESS`，前端**不得**用 message 判成败，只认 `code === 0`）。
- `data` 可为 `null`；失败时 `data` 一般不填，**唯一例外是 4090 时段冲突**——必须带冲突明细（`conflictDates` / `suggestRanges`），见 §8.3「错误码口径」。实现上由 `BizException(ErrorCode, String, Object)` 承载，`GlobalExceptionHandler` 原样写入 `Result.data`。
- `traceId` 由 `TraceIdFilter` 写入 MDC，响应体与响应头 `X-Trace-Id` 同值；前端报错截图时凭它定位日志。
- Controller **必须**返回 `Result<T>`，禁止直接返回 Entity 或 `Map`。
- 分页统一 `PageResult<T> { List<T> list; long total; long pageNum; long pageSize; }`（字段名以已实现的 `common/result/PageResult` 为准，前端按 `data.list` 取）。

### 7.2 错误码段位（**禁止自定义新段位**）

| 段位 | 领域 | 已占用（与 `common/constant/ErrorCode` 一一对应，改这里必须改枚举） |
| :-- | :-- | :-- |
| `0` | 成功 | `0 SUCCESS` |
| `1xxx` | 认证/鉴权 | `1001 UNAUTHORIZED`、`1002 FORBIDDEN`、`1003 TOKEN_INVALID`、`1004 TOKEN_EXPIRED`、`1005 ACCOUNT_OR_PASSWORD_ERROR`、`1006 ACCOUNT_DISABLED` |
| `2xxx` | 参数/校验 | `2001 PARAM_ERROR`、`2002 PARAM_MISSING`、`2003 PARAM_FORMAT_ERROR`、`2004 FILE_TYPE_NOT_ALLOWED`、`2005 FILE_TOO_LARGE` |
| `3xxx` | 状态机/权益 | `3001 ORDER_STATE_INVALID`、`3002 CREDIT_NOT_ENOUGH`、`3003 ITEM_STATUS_INVALID`、`3004 ITEM_NOT_FOUND`、`3005 ORDER_NOT_FOUND`、`3006 SELF_RENT_FORBIDDEN`、`3007 ITEM_AUDIT_PENDING`、`3008 PERMISSION_DENIED_FOR_RESOURCE`、`3009 APPEAL_STATE_INVALID` |
| `4xxx` | 费用 | `4001 FEE_OUT_OF_RANGE`、`4002 DEPOSIT_INVALID`、`4003 SETTLEMENT_ALREADY_DONE`、`4004 LOSS_RATIO_INVALID` |
| `4090` | **时段冲突**（`4xxx` 段的显式豁免单号，压测断言依赖此固定值） | `4090 OCCUPANCY_CONFLICT` |
| `5xxx` | 系统/基础设施（**内部诊断用，不下发给终端用户**） | `5000 SYSTEM_ERROR`、`5001 DB_ERROR`、`5002 CACHE_ERROR`、`5003 LOCK_ACQUIRE_FAILED`（仅日志/定时任务，下单接口必须转 4090，见 §8.3）、`5004 REMOTE_CALL_ERROR` |

> **关于 4090 的规则豁免**：本表规定 `4xxx` = 费用，而 `4090` 表示时段冲突，属**唯一一处显式例外**。原因：K2 压测脚本与前端 F7 都硬编码断言 `4090`，语义改成「费用类」反而会让「时段冲突」被当成金额问题提示用户。除此之外**不得**再出现跨段位编号；要加新码只能在本表已有码后面递增。

新增错误码必须先 `grep` 确认未被占用，并**同时**更新 `common/constant/ErrorCode.java`、本节表格与 `docs/接口文档.md` 三处，缺一处视为未完成。

**1001 / 1003 / 1004 的分工（已实现，改鉴权时必须保持）**：三条码必须都能真实产生，否则前端只能「一律跳登录」。

| 场景 | 返回码 | 产生位置 |
| :-- | :-- | :-- |
| 完全没带 `Authorization` 头 | `1001` | `SecurityConfig` EntryPoint 默认值 |
| 带了但签名/格式非法、或 claims 缺 username/role | `1003` | `JwtUtil.resolveWithReason` → `JwtAuthenticationFilter` 写入 `request` attribute `rental.auth.error` → EntryPoint 读取 |
| 带了但已过期 | `1004` | 同上 |

- 过滤器**禁止**自己写响应体（会导致 `/api/ping` 与业务接口两套 JSON 格式），只负责「建登录态」或「把失败码放进 attribute」。
- 对应的回归测试：`JwtAuthenticationFilterTest` 四例（合法/过期/篡改/无头）；改动鉴权链路必须同步跑它。

**双令牌规则（已实现：`security/JwtUtil` + `security/RefreshTokenStore`，改鉴权必须遵守）**

| 项 | access | refresh |
| :-- | :-- | :-- |
| 有效期 | 2 小时（`jwt.expire-minutes: 120`） | 7 天（`jwt.refresh-expire-days: 7`） |
| claims | `sub`/`username`/`role`/`typ=access` | `sub`/`jti`/`typ=refresh`（**不带角色**） |
| 用途 | 所有业务接口的 `Authorization: Bearer` | 只用于 `POST /api/auth/refresh` |

- **类型必须互斥**：拿 access 调刷新接口 → `1003`；拿 refresh 调业务接口 → 过滤器只认 access，同样 `1003`。少了这道校验，2 小时有效期形同虚设。
- **refresh 不带角色是故意的**：刷新时重新查库取最新角色，否则管理员被降权后还能用旧 refresh 换出高权限 access。
- **服务端白名单**：Redis `auth:refresh:{userId}` → 当前 `jti`，TTL 7 天。刷新即覆盖（旧 refresh 立刻失效），登出删 key。JWT 自包含，没有这份白名单就收不回令牌。
- 注册即发令牌；`user` 与 `user_role(USER)` 必须**同事务**；初始信誉分读 `credit_policy.init_score`，**禁止写 80 字面量**。
- 密码错与用户不存在返回**同一个** `1005`（区分即等于提供手机号枚举接口）；`status != NORMAL` → 登录与刷新都 `1006`。
- 回归测试：`AuthIntegrationTest` 8 例（注册/重号/分流/1005 合并/1006/轮换/类型互斥/登出）。

**唯一索引冲突必须按约束名分流**（`GlobalExceptionHandler.handleDuplicateKey`）：

| 约束名 | 返回 | 理由 |
| :-- | :-- | :-- |
| `uk_user_phone` | `2001`「手机号已注册」 | 用户动作是「换个手机号」 |
| `uk_occupancy` | `4090`「该时段已被占用」 | K2 兜底，压测断言依赖 |
| 未识别 | `4090` + `unmapped duplicate key` 日志 | 新表唯一键冲突不许冒成 500，同时留线索补分流 |

禁止「一律转 4090」：注册时重复手机号会被前端按日期冲突提示，用户照着改日期也解决不了。

### 7.3 分页与参数

- 分页入参固定 `pageNum`（从 1 起）、`pageSize`（默认 10，**上限 100**，超出截断不报错）。
- 所有请求 DTO 必须用 `jakarta.validation` 注解（`@NotNull`、`@Min`、`@Future` 等），Controller 参数加 `@Valid`。
- 禁止把 `HttpServletRequest` 传进 Service。
- 当前登录用户从 `SecurityContextHolder` 或自定义 `@CurrentUser` 注解取，**禁止**从请求参数里信任 `userId`（防越权）。

### 7.4 序列化与格式契约（与 `docs/接口文档.md` §0 一一对应）

| 对象 | 规则 | 落地位置 / 禁止 |
| :-- | :-- | :-- |
| `LocalDateTime` | 输出**必须** `yyyy-MM-dd HH:mm:ss`，入参同格式 | `common/config/JacksonConfig`（`Jackson2ObjectMapperBuilderCustomizer` + `SimpleModule`）。**`spring.jackson.date-format` 只作用于 `java.util.Date`，对 JSR-310 无效**，只配 yml 会输出 ISO `2026-11-16T20:36:43` |
| `LocalDate` | 输出/入参**必须** `yyyy-MM-dd` | 同上（同一个 `SimpleModule`） |
| `Long` 主键 | 一律 JSON **字符串** | 实体/VO 字段加 `@JsonSerialize(using = ToStringSerializer.class)`；前端禁 `Number(id)`（F9） |
| 金额 | `BigDecimal` → JSON number，前端只展示 `toFixed(2)`，**不在前端算钱**（F1） | 规则 §6.1 |
| 需要回给前端的库默认值列（如 `created_at`） | `insert` 后**必须** `selectById` 重查再返回 | MyBatis-Plus 不会回填库默认值，直接返回实体会是 `null`（注册响应 `user.createdAt` 即此坑） |

**验证要求**：改序列化行为必须补测试，断言里写 `doesNotContain("T")` 之类的负向条件（参考 `JacksonConfigTest`），只断言"有值"不算验证。

---

## 8. 异常与事务规范

### 8.1 异常

- 业务异常统一抛 `BizException(ErrorCode)`（已实现：`common/exception/BizException`，构造参数为 `ErrorCode` 枚举），**禁止** `throw new RuntimeException("xxx")`。
- 全局 `@RestControllerAdvice` 统一转换，**禁止**在 Controller 里 `try-catch` 包一层返回 `Result.error`。
- **禁止**向客户端暴露堆栈、SQL、类名。
- **必须**捕获并转换的特定异常：`DuplicateKeyException` **按约束名分流**（见 §7.2 同表）—— `uk_occupancy` → `4090`；`uk_user_phone` → `2001`「手机号已注册」；其余未识别的唯一键 → `4090` 且日志 `log.warn("unmapped duplicate key：{}", detail)`。**禁止**把所有唯一键冲突一律转 4090（重复手机号会弹出"该时段已被占用"）。

### 8.2 事务

- `@Transactional(rollbackFor = Exception.class)` 加在 **public** 的 Service 实现方法上。
- **禁止**同类内部方法自调用（事务失效）；需要时拆到另一个 Bean。
- **禁止** `private` / `final` / `static` 方法加事务注解。
- 事务内**禁止**做 HTTP 调用、发消息、写 Redis（放到事务提交后，用 `TransactionSynchronization` 或事件）。
- 事务边界只在 Service；Controller 与 Mapper 不加。

### 8.3 并发（K2 专用，不可省略任何一层）

下单落库路径**必须**按此顺序，且**三层都在**：

```
① 应用层预检（不进事务）：查该物品未终结订单 → 内存区间比对 → 快速拒绝
② 缓存层加锁（在事务外！）：Redis SET key val NX PX 10000，key = "lock:item:{itemId}"
                            释放用 Lua 校验持有者后 DEL（禁止裸 DEL）
③ 数据库层（事务内）：INSERT order → 按 [start_date, end_date) 逐日展开
                     → 批量 INSERT order_occupancy（N 天 = N 行）
                     → 依赖 uk_occupancy(item_id, occupy_date) 兜底，catch DuplicateKeyException → 4090
```

#### ⚠️ 三个必须写清的前提（漏一个，K2 就只剩个空壳）

**前提 1：占用记录必须「逐日展开」，一天一行。**

这是唯一索引能生效的**全部原因**。跨 3 天（`10-01` 至 `10-04`）的订单，`order_occupancy` 必须插 **3 行**：`(item_id, 10-01)`、`(item_id, 10-02)`、`(item_id, 10-03)`。

```
❌ 错误理解（一笔订单一行，只存 start_date + end_date）：
   两笔订单的 (item_id, start_date) 天然不同 → 唯一索引永远不触发 → 第三层防线形同不存在，
   并发下会真的插入两笔重叠订单。

✅ 正确：区间 [start, end) 半开，右端点不占日
   LocalDate d = startDate;
   while (d.isBefore(endDate)) { rows.add(new OrderOccupancy(orderId, itemId, d)); d = d.plusDays(1); }
   // 10-01 ~ 10-04（还物日）→ 占用 10-01/10-02/10-03 三天，10-04 可被下一单从 10-04 起租
```

**前提 2：加锁必须在事务外，解锁必须在事务提交之后。**

若写成「`@Transactional` 方法内 `try { ... } finally { unlock(); }`」，锁在**方法返回时**释放，而事务在**方法返回之后**才提交 —— 中间有一个「锁已放、数据未提交」的窗口，后一个请求的预检读不到前一笔数据，会一路通过预检再由唯一索引抛 `DuplicateKeyException`。结果虽然不超卖，但退化成「抛异常」而非友好拒绝，压测时会看到大量 4090。

**正确结构**（加锁方法不带事务注解，事务在被调用的 Service 方法上）：

```java
// ✅ Controller/门面层：加锁 → 调事务方法 → 事务提交后再解锁
public Long createOrder(OrderCreateDTO dto) {
    String key = "lock:item:" + dto.getItemId();
    String val = UUID.randomUUID().toString();
    // 抢不到 → 按"错误码口径"伪装成 4090（**不是** 5003），且必须先做 500ms/10 次重试
    if (!redisLock.tryLock(key, val, Duration.ofSeconds(10))) throw orderConflict();  // code 4090 + 冲突明细
    try {
        return orderTxService.createInTx(dto);   // ← 这个方法标 @Transactional，返回即已提交
    } finally {
        redisLock.unlock(key, val);              // ← 此刻事务已提交，锁才放
    }
}
```

若确实要把解锁写在同一方法内（不推荐），则必须改用 `TransactionSynchronizationManager.registerSynchronization(...)` 在 `afterCompletion` 里 `unlock`，**禁止**在 `finally` 里直接放。

**前提 3：预检结果不可信，只是「省钱」，不是「安全」。**
预检失败就直接拒（省一次锁 + 一次事务）；预检通过也只是**允许进入**加锁与事务，最终裁决权在唯一索引。三层任何一层被写成「if 判断了就返回成功」，都算 K2 失败。

**前提 4：占用口径必须三处一致 —— `PENDING` 也算占用。**

订单创建即 `PENDING` 并立刻写 `order_occupancy`（否则"待确认"期间同一时段还能被再下一单，K2 直接破）。因此凡是"这个物品这几天有没有被占"的查询，状态集合**必须**是 `('PENDING','CONFIRMED','IN_USE','OVERDUE')`，**只写三个已确认状态是 bug**：

| 位置 | 必须一致的状态集合 |
| :-- | :-- |
| `OrderOccupancyMapper.xml` 的 `selectOccupiedDates`（下单冲突判定） | `PENDING, CONFIRMED, IN_USE, OVERDUE` |
| `ItemMapper.xml` 的 `selectOccupiedItemIds` / `selectOccupiedDates`（列表 `available`、详情日历） | 同上（已实测踩过：漏 `PENDING` 会导致刚下单的物品仍显示"可租"） |

`已完成 / 已取消` 的订单其占用行在状态流转时释放，不在集合内。


#### 错误码口径：**锁失败不许漏成 5003**（K2 压测断言依赖此条）

用户视角里不存在「锁」，只有「这个时段能不能租」。因此**抢锁失败必须对用户伪装成业务冲突**：

```
抢锁（SET NX PX）失败
  → 500ms 内轮询重试：每 50ms 再试 NX，最多 10 次（总预算 ≤500ms）
  → 期间抢到：正常走事务
  → 预算耗尽仍失败：抛 BizException(OCCUPANCY_CONFLICT) 即 4090，附带冲突日期与候选时段
```

- `5003 LOCK_ACQUIRE_FAILED` **只用于**内部诊断日志与非用户路径（定时任务、补偿任务），**禁止**作为下单接口的对外返回码。
- 禁止「锁失败即返回 5003」——前端只会弹「系统繁忙」，用户永远不知道该换个日期，K2 的验收观感直接崩。
- 禁止「锁失败直接 sleep 重试无限次」——会把 Tomcat 线程池耗尽，比冲突严重得多。预算 500ms 是硬上限。

**压测断言（写进 `docs/测试用例.md`，W7 交付物）**：同一 item、同一时段、50 线程并发下单 →

| 断言项 | 期望值 |
| :-- | :-- |
| `code == 0` 的响应数 | **恰好 1** |
| `code == 4090` 的响应数 | **49** |
| `code == 5003 / 5000` 的响应数 | **0** |
| `SELECT COUNT(*) FROM order_occupancy WHERE item_id=? AND occupy_date IN (...)` | 等于该订单天数，无重复行 |

50 个响应里出现任何 `5003` 或 `5000`，说明前提 2（锁在事务外）或本节的重试口径没做到，压测不算通过。

**禁止的「简化」**（交了就是错误）：

- ❌ 只用 `SELECT ... WHERE` 判断后插入
- ❌ 用 `synchronized` 或本地 `ReentrantLock` 替代（单机假象，且与事务边界冲突）
- ❌ 用 `SELECT ... FOR UPDATE` 但不在事务内
- ❌ 锁 key 用 `lock:order`（粒度太粗）、TTL 不设（死锁）
- ❌ `order_occupancy` 一笔订单只插一行、或先删后插（删除窗口内并发照样穿透）
- ❌ 在事务内 `unlock`（见前提 2）
- ❌ 捕获 `DuplicateKeyException` 后 `printStackTrace` 返回 500（必须转 4090 并带冲突日期）
- ❌ 锁失败直接透出 `5003`、或无限重试

---

### 8.4 订单状态机规范（K2 的下游，K3/K4 的入口）

**一、状态迁移一律用「条件更新」，不许「先查再改」。**

```java
// ✅ 一条 SQL 完成「检查 + 迁移」，并发下只有一个请求能成功
int rows = rentalOrderMapper.update(null, Wrappers.<RentalOrder>lambdaUpdate()
        .set(RentalOrder::getStatus, OrderStatus.CONFIRMED)
        .set(RentalOrder::getConfirmedAt, LocalDateTime.now())
        .eq(RentalOrder::getId, orderId)
        .eq(RentalOrder::getStatus, OrderStatus.PENDING));

// ❌ 先 selectById 判断、再 updateById —— 两次点击并发时会双写时间戳与状态
```

| 规定 | 内容 |
| :-- | :-- |
| 失败错误码 | 受影响行数 `0` → **`ORDER_STATE_INVALID`（3001）**，文案必须说明「需要什么状态、当前是什么状态」。**绝不许**用 `ITEM_STATUS_INVALID`（3003，那是「物品不可租」） |
| 状态迁移放哪 | 独立 Service（`OrderStateService`）+ `@Transactional`。`OrderServiceImpl`（加锁那层）**不许**标 `@Transactional` |
| 谁释放占用 | 只有 **取消（cancel）** 与 **归还（return）** 删 `order_occupancy`；确认/取用/逾期扫描一律不动占用 |
| 物品状态联动 | `pickup` → 物品 `RENTED`；`cancel` → 物品从 `RENTED` 回 `ON_SHELF`（只在无其它进行中订单时） |
| 幂等 | 重复点击靠状态条件天然幂等：第二次 `rows=0` → 3001，不会双写、不会重复扣分 |
| 定时任务 | cron **从 `sys_config` 读**（缺失/非法回退默认值并 warn）；`runOnce()` 必须 catch 全部异常（一次失败不许让任务停摆）；**必须同时提供管理端手动触发接口**，且两者调用同一个方法 |

**二、写状态机测试时注意两个坑**

1. 物品一旦 `RENTED` 就不能再下单 —— 测「取消释放占用后别人能重新下单」时，**必须换一件物品**，否则拿到的是 3003 而不是预期的 2001/4090。
2. `SchedulingConfigurer` 的 `CronTrigger.nextExecution(...)` 返回 `Instant`，不要再包一层 `LocalDateTime`。

---

## 9. 配置外置规范（**不许硬编码**）

以下数值**必须**从配置表或 `application.yml` 读取，**禁止**在 Java 代码里出现字面量：

| 类别 | 键 | 默认值 |
| :-- | :-- | :-- |
| 信誉分 | `credit.init` / `.max` / `.min` | 80 / 100 / 0 |
| 信誉分 | `credit.reward.on_time` / `.daily_cap` | +2 / +4 |
| 信誉分 | `credit.penalty.overdue_light` / `.overdue_heavy` / `.damage` / `.cancel_after_confirm` | −5 / −10 / −10 / −3 |
| 信誉档 | 免押 ≥95、正常 60–94、上浮 40–59（×1.5）、暂停 <40 | — |
| 费用 | `fee.overdue.tier1_days` / `.tier1_rate` / `.tier2_rate` | 3 / 0.5 / 1.0 |
| 费用 | `fee.overdue_rate.min` / `.max` | 0.1 / 2.0 |
| 费用 | `fee.daily_price.min` / `.max` | 1 / 500 |
| 损耗 | `loss.threshold.maintain` / `.repair` | 0.6 / 0.85 |
| 损耗 | 租期/类目/归还状态系数 | 见需求总结 §5 |
| 推荐 | `recommend.w.cf` / `.content` / `.heat` | 0.6 / 0.3 / 0.1 |
| 推荐 | `recommend.decay.half_life`（天） | 30 |
| 订单 | `order.lock.ttl`（秒） | 10 |
| 社区 | `community.priority.enabled`（列表/推荐「同小区 > 同片区」优先档开关，0 = 关闭，供消融对比） | 1 |
| 基础设施 | `file.root` / `.access-path` / `.max-size-mb` / `.allowed-ext` | `uploads` / `/uploads` / 5 / `jpg,jpeg,png,webp` |
| 基础设施 | `jwt.secret` / `jwt.expire-minutes` / `jwt.refresh-expire-days` | 见 `application-dev.yml` / 120 / 7 |

**实现方式**（本项目**没有** `spring-cloud` 依赖，`@RefreshScope` 用不了，别写）：

1. **加载**：策略类配置放 `credit_policy` / `fee_policy` / `loss_policy` 三张表，系统开关放 `sys_config`；用一个 `@ConfigurationProperties(prefix = "policy")` 的 `PolicyProperties` 承接，启动时由 `ApplicationRunner` 全量读库填充，整体存进 `AtomicReference<PolicySnapshot>`（业务代码只读快照，避免读到半更新状态）。
2. **刷新**（两条腿，都要有）：
   - **主动**：管理端 `POST /api/admin/config/refresh` → 调 `PolicyLoader.reload()`。**演示用这条**，点一下就生效，比等 60 秒好看。
   - **兜底**：`@Scheduled(fixedDelay = 60_000)` 比对 `update_time` 最大值，变了才重载（没有变化就不查全表）。
3. **禁止**：`@RefreshScope`（编译不过）、每次业务实时查配置表（性能）、用 `static` 变量缓存配置（刷不掉）、把默认值直接写成代码字面量又「顺便」放个配置文件双份。

> **验收方式**：改一行数据库配置 → 点管理端「刷新配置」→ 系统行为立即改变，**不重启、不重新编译**。这是「规则外置」的证据，答辩演示会用到；只把值挪进 yml 不算达标（改 yml 要重启，证明不了什么）。

> **唯一的例外：基础设施配置**。`file.*` 四项（存盘目录、访问前缀、大小上限、扩展名白名单）走 `application.yml`，**不进策略表**——它们不改变业务规则，只告诉程序「文件落在哪个盘」；改它们必须重启也无可演示性。判定标准只有一条：**这个值变了，租用/计费/信誉/损耗的行为会不会变？** 会变 → 进表；不会变 → 进 yml。

---

## 10. 日志规范

- 用 SLF4J `@Slf4j`，**禁止** `System.out.println`。
- 级别：`error` 系统异常/需人工介入；`warn` 业务拒绝（冲突、权限不足、信誉不足）；`info` 状态流转关键节点；`debug` 明细。
- 关键路径（下单、结算、状态迁移、定时任务）日志**必须**带 `orderId`，必要时带 `traceId`。
- **禁止**打印密码、token、完整手机号（脱敏为 `138****1234`）。

---

## 11. 注释与文档规范

- 类、public 方法必须有 Javadoc（中文），说明**做什么、参数含义、可能抛出的业务异常码**。
- 复杂业务逻辑（冲突判定、阶梯计费、损耗加权）**必须**有行内注释解释**为什么**这么算，而不是复述代码在做什么。
- **禁止**写 `// TODO` 后声称任务完成。有 TODO 就是未完成。
- 每个 K 点（K1–K6）完成后，写一篇 `docs/notes/{K}x-主题.md`，三段：问题 / 方案 / 验证与踩坑。这是论文第 4 章的素材。

---

## 12. 测试规范

- 框架：JUnit 5 + Spring Boot Test + Mockito。
- **Mockito 必须禁用 inline mock maker**：本机 JDK `17+35-LTS-2724` 不提供可用的 agent 自挂载，`@SpringBootTest` 一跑就报 `Could not initialize inline Byte Buddy mock maker`。`backend/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker` 内容固定为 `mock-maker-subclass`（这是配置文件，不是新增依赖）。用它之后无法 mock `final` 类/静态方法——需要 mock 它们说明设计该改。
- 集成测试（`*IntegrationTest`）连真实 `rental` 库，类上加 `@Transactional` 自动回滚；**禁止**用 H2 或改连测试库（§2.1 禁新增依赖）。
- 新增核心业务方法（冲突判定、费用计算、信誉变更、损耗累计）**必须**配单元测试。
- 测试命名：`方法名_场景_期望结果`，例如 `createOrder_overlapRange_throwsTimeConflict`。
- 必须覆盖的边界（详见 `docs/测试用例.md`）：
  - `[1,3)` vs `[3,5)` → 不冲突；`[1,3)` vs `[2,4)` → 冲突
  - 逾期正好 3 天 vs 4 天（阶梯分界）
  - 押金 − 逾期费 − 损耗费 < 0 → 待补缴
  - 信誉分 100 封顶 / 0 下限 / 一天多单只加 4 分
  - 结算幂等：连调两次返回同一 `settle_no`
- 并发验证：`CountDownLatch` + 线程池 50 线程打同一时段，断言**成功订单数 == 1**，且另外 49 个响应**全部是 `4090`**、没有 `5003`/`5000`（口径见 §8.3），并校验 `order_occupancy` 无重复行。

---

## 13. AI 协作行为准则（**每条都会被执行检查**）

| # | 准则 | 反例 |
| :-- | :-- | :-- |
| 1 | **先说方案，再写代码**。每个任务第一次回复只给方案（改哪些文件、每个方法做什么），等我确认 | 直接甩 300 行 |
| 2 | **只改我点名的文件**。改动范围超出时必须先说明 | 顺手重构了三个不相关类 |
| 3 | **不新增依赖**（§2.1） | 为了日志加个 `log4j2` |
| 4 | **不写 TODO 当完成** | `// TODO: 后面再补锁` 然后说做完了 |
| 5 | **不用已废弃 API**（§2 表格右列） | `javax.servlet`、`WebSecurityConfigurerAdapter` |
| 6 | **不擅自改表结构 / 不加表** | 为了图省事加一张 `order_ext` |
| 7 | **涉及 K2/K3 必须解释原理**：为什么这样能防超卖、为什么这样幂等 | 只给代码不说理由 |
| 8 | **每个实现必须附验证方式**：一条 curl、一个测试方法，或一句「怎么点能看出来」 | 「已完成」三个字 |
| 9 | **中文注释、中文回复**；代码标识符用英文 | 中文变量名 |
| 10 | **不确定就问，不要编**。缺字段、缺枚举值、缺接口时明确说「这里需要你确认 X」 | 自己发明一个 `status=9` |
| 11 | **提交前逐条报告 §14.3 清单**：每条答「是/否/不适用」并给出证据（命令输出摘要、测试方法名） | 只回「已完成」「已测试，没问题」 |
| 12 | **不许在测试未通过时声称完成**。挂了就说挂了，并区分「已修」与「已知未修」 | 嘴上说通过，实际 `-DskipTests` |

### 13.1 明确不做（AI 不得主动实现）

见 `社区物品租用平台-需求总结.md §10.2` 全表。摘要：**真实支付、实名认证、物流配送、实时 IM、视频上传、小时级租用、转租拼租、微服务/MQ、小程序/App、线上 A/B、深度安全加固、发票税务、多租户、财务折旧模型。**

遇到灰区需求，按 `需求总结 §10.3` 五问判据裁决，**不要自行决定纳入**。

---

## 14. 提交规范

### 14.1 commit message

```
<type>(<scope>): <中文简述>

type: feat | fix | refactor | test | docs | chore | perf | wip
scope: order | settlement | credit | recommend | loss | item | user | web | build
```

> `wip` 是**检查点专用**前缀，语义见 §14.2。除 `wip` 外，其余 type 一律视为「正式提交」，必须过三道闸门。

示例：

```
feat(order): 三层冲突检测与占用日唯一索引兜底
fix(settlement): 修正逾期阶梯分界点少算一天
test(credit): 补齐日加分封顶与 0 分下限用例
```

### 14.2 提交分两档：检查点（wip）与正式（feat/fix/…）

> **核心区分**：不是每次 commit 都要全绿，但**每次 commit 都必须标明自己是哪一档**。
> 把所有提交都要求「测试全绿」会导致一个更糟的结果——**你不敢提交了**，一天只 commit 一次，出了问题回滚到八小时前。所以分档。

| 档位 | 前缀 | 用途 | 门槛 | 允许 | 禁止 |
| :-- | :-- | :-- | :-- | :-- | :-- |
| **A 检查点** | `wip(scope):` | 存档、AI 大改前留退路、一天结束 | **仅需 G1 编译通过** | 本地 commit | 打 tag · 宣称功能完成 · 合并到主分支 · 连续两次 |
| **B 正式** | `feat` `fix` `refactor` `test` `perf` | 一个功能点确实做完了 | **G1 + G2 + G3 三道闸门全过** | commit + tag + 进入下一功能点 | 跳过任何一道闸门 |

**B 档的三道闸门（缺一不可）**

| 闸门 | 命令 / 动作 | 通过标准 |
| :-- | :-- | :-- |
| **G1 编译** | `cd backend; mvn -q clean compile` | 零错误（Maven 3.9.9 已装，见 §3；**在 `backend/` 目录下执行**，项目根没有 pom） |
| **G2 单测** | `cd backend; mvn -q test` | 本次改动相关的测试**全绿**；新增业务方法必须有对应测试 |
| **G3 边界** | 按 `docs/测试用例.md` 对应条目逐条验证 | 结果与预期一致（不是"看着没报错"） |

**三条硬规则**

1. **测试挂了只能提交 `wip:`**。想存档就用 wip 前缀，**不许**用 `feat:` 掩盖未通过的测试。
2. **禁止连续两个 `wip:`**。出现即说明在攒垃圾；第二个 wip 之前必须先把上一个修成绿。
3. **`-DskipTests` 只允许在排查环境问题时临时用**，不得写进提交说明，更不得用于「证明完成」。

### 14.3 提交前自检清单（每次 B 档提交前逐条打勾）

- [ ] G1：`mvn -q clean compile` 零错误
- [ ] G2：`mvn -q test` 全绿，且本功能有新增/更新的测试
- [ ] G3：`docs/测试用例.md` 中对应边界条目已勾选并通过
- [ ] 无 `// TODO` 遗留
- [ ] 无硬编码费率/阈值（必须来自配置表，见 §9）
- [ ] 改动范围限于本次任务（`git diff --stat` 已检查，未误伤其他文件）
- [ ] 无敏感信息（密码、token、完整手机号）
- [ ] commit message 符合 §14.1
- [ ] 若是 K1–K6 相关，已写 `docs/notes/{K}x-主题.md` 三段笔记

> **用法**：提交前把这份清单贴给 AI，要求它逐条回答「是 / 否 / 不适用」并附证据（哪条命令、哪个测试方法名）。**不接受"已完成""已测试没问题"这类回答。**

### 14.4 节奏

- 一个功能点一次 **B 档** commit；每个 K 点完成打 tag：`k2-conflict-done`、`k3-settlement-done`…
- 让 AI 大改之前先 `wip(checkpoint): 改动前存档`，改崩了直接 `git checkout .` 回滚，**不要让 AI 修自己的烂摊子**。
- 一天结束无论什么状态都提交一次（A 档 `wip`），**但第二天第一件事是把它修成 B 档**。
- 阶段结束（每 4–5 周）必须处于「全部 B 档、无遗留 wip」状态，否则不进入下一阶段。

---

## 15. 参考文档索引

| 文档 | 用途 |
| :-- | :-- |
| `社区物品租用平台-课题说明书.md` | 立论、公式、答辩口径（**最高优先级**） |
| `社区物品租用平台-需求总结.md` | 需求清单 FR、优先级、边界判定、20 周排期 |
| `docs/AI协作开发手册.md` | **方法论**：七步闭环、四类提示词模板、边界用例清单、上下文管理、答辩防守。**不含本文件的规则副本**——同一规则只在本文件维护，两处冲突时以本文件为准 |
| `项目进度.md` | **做到哪了**：三条硬指标现状、已完成项与验证证据、未完成项依赖顺序、环境速查、已知坑（每次可提交单元完成后更新） |
| `docs/数据库设计.md` | 表结构、索引、字段理由、K 点约束落地对照、实测记录（已完成，19 表） |
| `docs/接口文档.md` | **契约唯一真源（人读）**：全局约定（分页/id/时间/金额）、错误码表、4090 形状、已冻结接口逐字段、未冻结接口清单、变更记录（改契约必须同步本文件并在提交说明打 `CONTRACT CHANGE:`） |
| `docs/前后端并行开发方案.md` | **怎么并行**：7 个垂直切片（0 骨架 → 1 账号 → 2 物品 → 3 下单 K2 → 4 流转 → 5 结算/信誉 → 6 推荐/看板）、契约三真源、mock 纪律、每切片 DoD |
| `docs/测试用例.md` | 边界用例清单（边开发边填） |
| `docs/notes/*.md` | 各 K 点技术笔记（论文素材） |

---

## 16. 本文件的修改规则

- 改技术栈版本、改目录结构、改错误码段位、加禁止事项 → **需要我确认**，并由我手动改。
- AI 不得自行修改本文件。
- 每次修改在下方留一行记录。

| 日期 | 修改人 | 内容 |
| :-- | :-- | :-- |
| 2026-11-10 | 项目负责人 | 初版 v1.0 |
| 2026-11-10 | 项目负责人 | v1.1：§14 提交门槛由「能编译即可」升级为**两档制**（`wip` 检查点 / 正式提交须过 G1+G2+G3），新增 §14.3 提交前自检清单，§13 增第 11、12 条（提交前报闸门结果、不许虚报测试通过） |
| 2026-11-10 | 项目负责人 | v1.2：①§2 连接池由 HikariCP 改为 **Druid 1.2.23**（`druid-spring-boot-3-starter`），新增 §4.8 Druid 配置契约 D1–D9；②补齐前端架构 §4.4 两个独立 Vite 应用目录、§4.5 前端分层职责、§4.6 前端硬性规则 F1–F14、§4.7 两端页面清单与 FR 映射 |
| 2026-11-10 | 项目负责人 | v1.2（续）：§3 环境表更新——Maven 3.9.9 已安装至 `C:\dev\apache-maven-3.9.9`（`MAVEN_HOME` + 用户 `Path` 已配），新增 Maven 镜像与 GBK 编码两行备注 |
| 2026-11-10 | 项目负责人 | **v1.3（外部审查后修订，12 条）**：①新增 §0 「铁律 12 条」摘要页；②§1「三者」改「四者」；③§2 MyBatis-Plus 由「3.5.5+」锁定为 **3.5.7** 并注明 3.5.9+ 需 `mybatis-plus-jsqlparser`；④§2 Redis 由「7.x」改为实测 **5.0.14.1** 并标注命令集限制；⑤§2 补 `dayjs`/`sass`/`unplugin-*` 三行，§2.1 增「已获批例外」；⑥§4.1 目录树重画（`src/main/resources` 与 `src/main/java` 同级，原缩进会被照建成错结构）；⑦§7.2 错误码表与已实现 `ErrorCode` 枚举对齐并加 4090 豁免说明；⑧§8.3 补三条前提——**逐日展开**、**锁在事务外/提交后释放**、预检不可信；⑨§9 去掉不可用的 `@RefreshScope`，改为 `@ConfigurationProperties` + 快照 + 管理端刷新 + 60s 定时兜底；⑩新增 D10（`characterEncoding=UTF-8`）并同步 §6.4；⑪§7.1 `PageResult` 字段名与实现对齐（`list`） |
| 2026-11-10 | 项目负责人 | v1.3（续）：§8.3 增「错误码口径」——抢锁失败 500ms 内轮询重试、耗尽转 **4090**（`5003` 仅限内部诊断），附 50 并发压测断言表（1 个 0 / 49 个 4090 / 0 个 5003）；§0 铁律 1、§7.2 `5xxx` 行、§12 并发验证同步 |
| 2026-11-10 | 项目负责人 | **v1.3.1（规则反查代码，代码回补）**：①§7.1 成功文案 `"ok"` 改为与 `ErrorCode.SUCCESS` 一致的 `"成功"`，补 `data` 为空的规则与 4090 例外；②§7.2 新增「1001/1003/1004 分工表」——过滤器只写 `rental.auth.error` attribute、EntryPoint 统一输出，禁止过滤器自写响应体；③代码侧同步：`BizException` 增 `data` 字段与 `occupancyConflict(msg,data)`、`Result` 增 `fail(code,msg,data)` 重载、`JwtUtil.resolveWithReason` 区分过期/非法、`JwtAuthenticationFilter` 不再吞掉失败原因；新增 `JwtAuthenticationFilterTest`(4)、`BizExceptionTest`(5)、`ResultTest` 扩至 7 例，共 **20/20 通过** |
| 2026-11-11 | 项目负责人 | **v1.4（文档索引）**：§15 参考文档索引新增 `项目进度.md`（回答「做到哪了」，含三条硬指标现状与未完成项依赖顺序），`docs/数据库设计.md` 状态由「待产出」更新为「已完成，18 表」。本版不改任何技术口径，仅改索引；配套实绩：数据库 18 表已落地（commit `8d2910a`、`c608282`），进度文档 commit `3871a2b` |
| 2026-11-11 | 项目负责人 | **v1.5（文档去重，确立单一真源）**：①`AI协作开发手册.md` 由根目录移入 `docs/`，瘦身 v1.0→v2.0（443 行 → 379 行），删除与本文件重复的四块（G1–G5 闸门表、`PROJECT_RULES` 模板骨架、14 条版本陷阱、一页速查图）与已完成的「今天就能做的 5 件事」；②手册新增「与本文件分工」声明，**同一规则只在本文件维护，冲突以本文件为准**；③修正手册两处与本文件的冲突（「每个能跑的版本打 tag、`wip` 也可打」→ 按 §14.2/§14.4 仅 K 点完成时打 tag、`wip` 不打；「用 Swagger 验证接口」→ 项目无该依赖，改 `curl` + 断言响应体 `code`）；④§4.1 目录树同步：移除根目录手册、补 `项目进度.md` 与 `sql/03-verify.sql`；⑤§15 索引行同步新路径与定位 |
| 2026-11-11 | 项目负责人 | **v1.6（实体层落地）**：①§4.1 目录树 `module/` 下新增 `system/`（`sys_config` 键值参数归属，原先 18 张表里这张无处安放）；②§12 新增两条测试前提——**Mockito 必须用 `mock-maker-subclass`**（本机 JDK `17+35` 无可用 agent 自挂载，inline mock maker 会让每个 `@SpringBootTest` 报 `Could not initialize inline Byte Buddy mock maker`）、集成测试连真实库 + `@Transactional` 回滚且禁用 H2；③配套实绩：18 实体 + 18 Mapper + `MybatisPlusConfig`（分页插件显式注册）+ `MapperIntegrationTest` 8 例，测试总数 20 → **28/28** |
| 2026-11-11 | 项目负责人 | **v1.7（文件上传落地）**：①§4.1 `module/system/` 注释扩为「系统支撑：配置参数 + 文件存储」，`FileController`/`UploadVO` 归此包（不新建 `module/file`，避免目录树膨胀）；②§9 配置清单追加 `file.*` 四项，并新增「唯一的例外：基础设施配置」——走 yml 不进策略表的判定标准是**「这个值变了，租用/计费/信誉/损耗的行为会不会变」**；③配套实绩：`FileStorageProperties` + `FileStorageUtil`（UUID 重命名防路径穿越）+ `WebMvcConfig`（`/uploads/**` 静态映射）+ `POST /api/files/upload`，`GlobalExceptionHandler` 补 `MaxUploadSizeExceededException`→2005，`SecurityConfig` 放行 `/uploads/**`（`<img>` 带不了 Authorization 头）；`FileStorageUtilTest` 7 例，测试总数 28 → **35/35** |
| 2026-11-16 | 项目负责人 | **v1.8（社区字典化）**：①§6 新增 6.5——社区统一存 `community_id`，禁止再出现社区字符串；`user.community_id` 可空、`item.community_id` 必填；就近一律整数等值分档「同小区 > 同片区 > 其他」，不做距离/地图；排序开关 `community.priority.enabled` 为 0 时必须退回原排序（论文消融入口，不许删分支）；②§9 配置清单增 `community.priority.enabled`（默认 1），信誉档门限改为「免押 ≥95、正常 60–94」；③§15 `docs/数据库设计.md` 更新为 19 表；④配套实绩：`community` 表 + `uk_community_city_district_name`、`user`/`item` 改列、`GET /api/communities`（匿名可读）、`CommunityServiceTest` 1 例，`03-verify.sql` 19 断言全 PASS、14 条负向全被拦，测试总数 35 → **37/37**；⑤口径变更已备案：需求总结 §14.1 两条（免押门槛 80→95、社区字段字典化） |
| 2026-11-17 | 项目负责人 | **v1.9（注册登录与双令牌落地）**：①§7.2 新增「双令牌规则」表——access 2h 只带 `sub/username/role/typ`、refresh 7d 只带 `sub/jti/typ`（**故意不带角色**，刷新时重查库）；`typ` 互斥校验（拿 access 调刷新 = 1003）；Redis 白名单 `auth:refresh:{userId}` 支撑登出与轮换；初始信誉分读 `credit_policy.init_score` 禁字面量；密码错与用户不存在合并 1005；`status != NORMAL` 登录与刷新都 1006；②§7.2 新增「唯一索引冲突按约束名分流」表——`uk_user_phone`→2001、`uk_occupancy`→4090、未识别→4090 + `unmapped duplicate key` 日志，禁止无条件转 4090；③§9 配置清单增 `jwt.secret`/`jwt.expire-minutes`(120)/`jwt.refresh-expire-days`(7)；④配套实绩：`AuthServiceImpl`/`UserServiceImpl`/`RefreshTokenStore`/`UserConvert` + 4 DTO + 2 VO + 2 Controller，`AuthIntegrationTest` 8 例，测试总数 37 → **45/45**；G3 实测注册→登录→me→refresh→logout→旧 refresh 1003→真 token 上传全链路通过 |
| 2026-11-17 | 项目负责人 | **v1.10（前端骨架落地 + 联调纠契约）**：①§2 前端依赖表按 `npm registry` 实测值补全，并**更正 `unplugin-auto-import` 为 `^0.19.0`**（0.x 线最后一版，原写的 `^0.27.0` 在 npm 上不存在、装不上）；新增 `@vitejs/plugin-vue 5.2.x`、`vue-router 4.x` 两行；②新增 §7.4「序列化与格式契约」——`LocalDateTime`/`LocalDate` 必须走 `JacksonConfig`（**`spring.jackson.date-format` 对 JSR-310 无效**，只配 yml 会输出 ISO `T` 格式）、Long 主键字符串、金额只展示、**库默认值列 insert 后必须重查**否则返回 null；③§8.1 `DuplicateKeyException` 由「一律 4090」改为按约束名分流，与 §7.2 对齐；④§15 索引新增 `docs/接口文档.md`（契约真源）与 `docs/前后端并行开发方案.md`（7 个垂直切片）；⑤配套实绩：`web-user`（5173）与 `web-admin`（5174）两个 Vite 应用、`request.js` 单例（4090 不弹全局提示）、`constants.js` 枚举单一来源、登录/注册/我的/概览页；前端 `npm run build` 两端成功（1730 / 1726 模块）；联调经 5173 代理发现并修 2 个契约缺陷（ISO 时间、注册 `createdAt` 为 null），新增 `JacksonConfigTest` 2 例，测试总数 45 → **47/47** |
| 2026-11-17 | 项目负责人 | **v1.11（ElMessage 样式缺陷）**：§4.6 F5 补充——`ElMessage`/`ElMessageBox`/`ElNotification` 是函数式 API，不进模板，`unplugin-vue-components` 的样式注入对它们无效；显式 `import { ElMessage } from 'element-plus'` 只拿到 JS 函数，须在 `src/main.js` 手工 `import 'element-plus/theme-chalk/el-message.css'`（+`el-message-box.css`）。症状极具迷惑性：**接口确实返回 2001，界面上却什么都没有**（消息 div 被渲染在文档流末尾、视口之外）。配套实绩：`web-user`/`web-admin` 的 `main.js` 各加两行样式引入，重跑 `npm run build` 后 `dist` 的 CSS 中 `.el-message{` 由 0 命中变为命中 |
| 2026-11-17 | 项目负责人 | **v1.12（记住密码与忘记密码落地）**：①§4.6 新增 **F15**——凭据只允许经 `utils/remember.js` 做 base64 混淆后进 `localStorage`，注释须写明「非加密」，密码/令牌禁止进日志与 URL，取消勾选或改密后必须清除；②§4.7 页面清单补「忘记密码 `/forgot`」（P0，与登录同档，用户进不去时要能从登录页一步到达）；③`POST /api/auth/reset-password` 的设计边界写进 `docs/接口文档.md` §1.9：**三种要素不匹配统一一句 2001 文案**（否则接口沦为账号探测器）、重置成功即作废该用户全部 refresh 令牌、以「知识校验」替代短信验证码（无通道，需求总结 §10）；④配套实绩：`ResetPasswordDTO` + `AuthServiceImpl.resetPassword`（`@Transactional`，只更新 `password_hash`，随后 `refreshTokenStore.remove`）+ `AuthController` 新端点 + `SecurityConfig` 放行；`AuthIntegrationTest` 3 例（改密后旧密码 1005 / 新密码可登录 / 旧 refresh 1003；三要素不匹配文案一致；未设社区账号跳过社区校验），测试总数 47 → **50/50**；前端新增 `utils/remember.js`、`views/auth/ForgotView.vue`、`LoginView` 的记住密码与忘记密码入口，`npm run build` 通过（含 `ForgotView` 独立分块）；⑤顺带修两处**测试自身缺陷**：`MapperIntegrationTest` 不再锁死 `user`/`user_role` 总行数（浏览器注册会合法地让条数增长，改为校验 4 个种子手机号存在 + 分页用下界断言）、`JwtUtilTest`/`JwtAuthenticationFilterTest` 的篡改用例改打**签名段首字符**（32 字节 HMAC 的 base64url 末字符含 2 个无关位，改末字符可能解码出相同签名 → 偶发假失败），并给过滤器测试补 `@BeforeEach` 清 `SecurityContextHolder`（Spring 测试残留登录态会污染它）；⑥口径变更已备案：需求总结 §14.1「找回密码：原不做 → 知识校验式重置」 |
| 2026-11-17 | 项目负责人 | **v1.13（前端视觉基线定档）**：①新增 §4.9——风格定档「温暖亲切 / 主色暖橙 / 宽松舒适 12px 圆角」，并规定**唯一取值来源**（两端 `variables.scss` 的色/字/距/圆角、`global.scss` 的 `:root` EP 覆盖、`constants.js` 的六状态 `color`、`AuthLayout`/`DefaultLayout` 作为版式样板）；②硬规则六条——主色分三层（`#ea580c` 交互填充 3.6:1 / `#f97316` 仅图形不承载小字 / `#c2410c` 白底文字 5.2:1）、字号只五档间距只七档、状态色只从字典取、主题改动必须 build 后 grep `--el-color-primary` 自查、不做暗色/插画/动效库/自定义图标、视觉打磨每次 ≤1 天且不得挤占 K2/K3；③配套实绩：`web-user` 重写 `variables.scss`（暖橙 21 个颜色 token + 五档字号 + 七档间距 + 三档圆角 + 三档暖色阴影）与 `global.scss`（`:root` 覆盖 EP 含 `-light-3/5/7/8/9`/`-dark-2`），新增 `layout/AuthLayout.vue`（左右分栏 + 纯 CSS 光斑 + 三条硬指标展示，≤900px 上下堆叠），`LoginView`/`RegisterView`/`ForgotView` 统一套用，`DefaultLayout` 重做顶栏（logo 方块 + 导航下划线 + 吸顶毛玻璃），`constants.js` 三张状态字典加 `color` 字段；`web-admin` 新增 `variables.scss`（管理端更紧凑一套）与同族 `global.scss`；两端 `npm run build` 通过（用户端含 `AuthLayout`/各视图独立分块） |
| 2026-11-18 | 项目负责人 | **v1.14（切片 2 前端落地 + 类目字典补契约）**：①§4.7 页面清单按实际交付校准——首页路由 `/home` → `/`、个人中心 `/profile` → `/mine`，并补两张「已交付 / 未交付」清单（路由与清单必须同步，改路由就改这行）；②新增 §4.6 **F16**——列表/详情/发布页要用的字典（类目、社区）只走公开字典接口，**禁止在前端写死类目名或小区名**，也禁止把字典塞进 `constants.js` 当"伪枚举"（字典是数据，会随管理端增删；枚举是契约，必须与后端逐字一致）；③配套实绩：后端补 `GET /api/categories`（`CategoryVO` + `CategoryService` + `CategoryController`，`SecurityConfig` 放行，`CategoryServiceTest` 1 例，测试总数 74 → **75/75**），并在 `docs/接口文档.md` 以 **CONTRACT CHANGE** 方式登记 §2.12（冻结 §2 时遗漏了发布/筛选都要用的类目数据源）；前端 `web-user` 新增 `api/item.js`、`api/category.js`、`components/ItemCard.vue`、`components/OccupancyCalendar.vue`、`views/item/{ItemListView,ItemDetailView,ItemPublishView,ItemMineView}.vue`，`HomeView` 的切片进度表换成真实物品卡片流 + 类目入口，`DefaultLayout` 补「逛物品 / 发布 / 我的物品」导航；`web-admin` 新增 `api/item.js`、`views/audit/AuditView.vue`（默认只看待审核、通过即上架、驳回必须写原因）、`constants.js` 补 `ITEM_STATUS`；两端 `npm run build` 通过；④联调实测 31 条断言全过（经 8081 实例 + 仓库内 `.g3_front.mjs`，脚本已删除）：字典 11/5 条、匿名列表 total 6、详情图集 2 张、日历占用 11-20/21/22、非法月份 2003、发布多图 → 驳回（审核意见透出）→ 编辑回待审且审核意见清空 → 通过 → 公开列表可搜 → 下架消失 → 删除，以及匿名 1001 / 普通用户访问管理端 1002 |
| 2026-11-19 | 项目负责人 | **v1.15（切片 3 后端 K2 落地）**：①§8.3 新增「前提 4：占用口径必须三处一致——`PENDING` 也算占用」，并给出两张 XML 的状态集合对照表（`OrderOccupancyMapper.xml` 的冲突判定、`ItemMapper.xml` 的列表 `available` 与详情日历）；实测踩过：漏 `PENDING` 会让刚下单的物品仍显示"可租"；②§8.3 的示例代码里 `tryLock` 失败原写 `BizException(LOCK_ACQUIRE_FAILED)`，与本节的"错误码口径"自相矛盾，改为转 4090；③§4.1 目录树补 `common/lock/`（`RedisLock`）、`common/config` 实际成员（`TraceIdFilter`/`MybatisPlusConfig`/`JacksonConfig`/`WebMvcConfig`/`FileStorageProperties`）、`module/order` 的双 Service 分工（`OrderService` 加锁不标事务 / `OrderTxService` 标事务）、`resources/mapper` 两个 XML；④配套实绩：`OrderStatus`、`RedisLock`（SET NX PX + Lua 比对删除）、`OrderQuoteDTO`/`OrderCreateDTO`（**不含任何金额字段**，金额一律服务端重算）、`OrderQuoteVO`/`OrderCreatedVO`/`OrderDetailVO`、`OrderService`+`OrderTxService` 及其实现、`OrderController`（`POST /api/orders/quote`、`POST /api/orders`、`GET /api/orders/{id}`）、`OrderOccupancyMapper.selectOccupiedDates` + XML；`docs/接口文档.md` 新增 §3（切片 3 冻结：九条口径、4090 `suggestRanges` 生成规则、并发承诺）并登记 3005 与 3004 的语义分工；`OrderIntegrationTest` 13 例 + `OrderConcurrencyTest` 1 例（50 线程：成功 1 / 4090 49 / 无 5003、5000 / 占用 3 行无重复），测试总数 75 → **89/89**；G3 端到端 19 条断言全过（试算 3 天 75+100=175、下单 `R202611190001`、PENDING 也占日历、他人 4090 带 3 个 `conflictDates` 与 3 个互不重叠 `suggestRanges`、第三方看详情 3008、自租 3006、匿名 1001）；⑤口径备案：**创建即 PENDING 并立即占时段**（若等确认才占，待确认期间可重复下单，K2 破） |
| 2026-11-19 | 项目负责人 | **v1.16（切片 4 订单状态流转）**：①新增 **§8.4 订单状态机规范**：状态迁移一律条件更新（`UPDATE ... WHERE id=? AND status=<旧态>`），受影响行数 0 → **3001 `ORDER_STATE_INVALID`**（绝不许用 3003，那是"物品不可租"）；状态迁移放独立 `OrderStateService` + `@Transactional`，`OrderServiceImpl`（加锁层）不标事务；**只有 cancel 与 return 释放 `order_occupancy`**，确认/取用/逾期扫描不动占用；`pickup` → 物品 `RENTED`、`cancel` → 回 `ON_SHELF`；定时任务 cron 从 `sys_config` 读（缺失回退并 warn）、`runOnce()` 必须吞异常、**必须提供管理端手动触发且与定时任务同一方法**；附两个测试坑（物品 `RENTED` 时不能下单，测取消释放占用要换物品；`CronTrigger.nextExecution` 返回 `Instant`）；②§4.1 目录树补 `OrderStateService+Impl`、`admin/` 的 `AdminOrderController`、`job/OrderScanJob`；③配套实绩：`OrderCancelDTO`、`OrderStateService`+`OrderStateServiceImpl`（`confirm`/`pickup`/`cancel`/`scanOverdue`、`requireChanged` 拼"需要旧态、当前现态"文案）、`ScanResultVO`、`AdminOrderController`（`POST /api/admin/orders/scan-overdue`）、`OrderController` 三个动作接口、`job/OrderScanJob`（`SchedulingConfigurer` + `CronTrigger`）；`docs/接口文档.md` 新增 §4（切片 4 冻结 4 接口 + §4.6 先写清切片 5 的归还与结算冷静期口径），原 §4/§5 降为 §5/§6，接口总数 24 → 28；`OrderStateIntegrationTest` 8 例，测试总数 93 → **101/101**；G3 18 条断言全过（承租方确认 3008、出租方三连动作、取用后取消 3001、租出中物品他人下单 3003、取消后日历释放、`scan-overdue` 返回 `{updated:1}` 且普通用户 1002）；④两处既有测试因用户真实下单变红，按"不改数据只改断言"修：`MapperIntegrationTest` 订单改按种子 `order_no` 计 3、占用改 `>= 3`；`ItemIntegrationTest` 日期筛选期望值改为「在架数 − 该窗口被占的在架数」；⑤口径备案：**结算冷静期（B 方案）**——归还生成 `settlement.status=PENDING_CONFIRM`，承租方 48h 内无异议转 `SETTLED`、提异议转 `DISPUTED` 并建 `appeal`、超时自动 `SETTLED`、管理端裁决转 `ADJUSTED`（窗口读 `sys_config` 的 `settlement.confirm.hours`），这是承租方对"出租方单方认定损耗"的对价手段 |
