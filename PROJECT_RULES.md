# PROJECT_RULES.md · 项目约定（AI 必读）

> **这个文件是什么**：给 AI 读的**契约文件**。它把「技术栈版本、代码规范、目录结构、禁止事项」一次性钉死，避免每次新会话 AI 凭训练记忆猜错版本、猜错包名、自作主张加功能。
>
> **怎么用**：每次开新会话，第一句话固定是——
>
> ```
> 请先读 PROJECT_RULES.md、社区物品租用平台-需求总结.md，然后开始本次任务。
> ```
>
> **效力**：本文件优先级 **高于** AI 的默认习惯，**低于** `社区物品租用平台-需求总结.md`（需求口径）。三者冲突时按此顺序裁决：说明书 > 需求总结 > 本文件 > AI 习惯。
>
> **版本**：v1.2 · 2026-11-10 · 配套说明书 385 行版、需求总结 v1.0

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
| ORM | MyBatis-Plus | **3.5.5+**，依赖名 `mybatis-plus-spring-boot3-starter` | `mybatis-plus-boot-starter`（Boot 2 专用） |
| 数据库 | MySQL | **8.0.39**（InnoDB） | 5.7 语法、`utf8` 而非 `utf8mb4` |
| 缓存 | Redis | 7.x（本机 Windows 服务） | 3.x 的 `Jedis` 直连写法 |
| 安全 | Spring Security | **6.x** | `extends WebSecurityConfigurerAdapter` |
| 令牌 | jjwt | **0.12.x** | 0.9 的 `Jwts.parser().setSigningKey()` |
| 连接池 | **Druid** | **1.2.23**，坐标 `com.alibaba:druid-spring-boot-3-starter` | `druid-spring-boot-starter`（Boot 2 专用，Boot 3 启动即报 `AutoConfiguration.imports` 缺失） |
| 构建 | Maven | **3.9.9** | Gradle（本项目不用） |
| 前端框架 | Vue | **3.4+** | Vue 2 的 `data(){return{}}` + `this.xxx` |
| 前端构建 | Vite | **5.x** | vue-cli / webpack |
| UI 库 | Element Plus | **2.5+** | Element UI（Vue 2 专用） |
| 状态管理 | Pinia | **2.x** | Vuex |
| 请求库 | Axios | 1.x | fetch 直调（除特殊场景） |
| 图表 | ECharts | 5.x | — |
| 运行时 | Node.js | **v24.10.0**（本机） | — |

### 2.1 依赖引入规则

- **不得擅自新增依赖**。需要新依赖时，先在回复里说明「为什么现有依赖做不到、体积多大、是否有安全风险」，等我确认。
- 每个新增依赖必须写进本节表格。
- 禁止引入：Dubbo、Nacos、Kafka、RocketMQ、Elasticsearch、ShardingSphere、XXL-Job、Sa-Token、JPA/Hibernate（与 MyBatis-Plus 二选一，本项目选后者）。

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
| Redis | 服务名 `Redis`，`127.0.0.1:6379` | 已运行 |
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
├── PROJECT_RULES.md              ← 本文件
├── AI协作开发手册.md
├── 社区物品租用平台-课题说明书.md
├── 社区物品租用平台-需求总结.md
├── .gitignore
├── README.md                     ← 启动说明
├── backend/                      ← Spring Boot 3.2
│   ├── pom.xml
│   ├── sql/                      ← DDL 与初始化数据
│   │   ├── 01-schema.sql
│   │   └── 02-data.sql
│   └── src/main/java/com/duyikang/rental/
│       ├── RentalApplication.java
│       ├── common/               ← 公共层（无业务）
│       │   ├── result/           ← Result, ResultCode, PageResult
│       │   ├── exception/        ← BizException, GlobalExceptionHandler
│       │   ├── constant/         ← 常量与枚举入口
│       │   ├── util/             ← RedisUtil, DateRangeUtil, FeeCalculatorUtil
│       │   └── config/           ← MybatisPlusConfig, RedisConfig, SecurityConfig, WebMvcConfig
│       ├── security/             ← JWT 生成校验、UserDetails、过滤器
│       ├── module/
│       │   ├── user/             ← 用户与权限（controller/service/mapper/entity/dto）
│       │   ├── item/             ← 物品
│       │   ├── order/            ← 订单与冲突检测（K2）
│       │   ├── settlement/       ← 费用结算（K3）
│       │   ├── credit/           ← 信誉分（K4）
│       │   ├── recommend/        ← 推荐（K5）
│       │   ├── loss/             ← 损耗检修（K6）
│       │   ├── appeal/           ← 申诉与纠纷
│       │   └── message/          ← 站内信
│       ├── admin/                ← 管理端专用接口（controller 层）
│       └── job/                  ← 定时任务（逾期扫描、结算补偿、推荐更新）
│   └── src/main/resources/
│       ├── application.yml
│       ├── application-dev.yml
│       └── mapper/               ← MyBatis XML（复杂 SQL 才用 XML）
├── web-user/                     ← 用户端 Vue 3 + Vite
├── web-admin/                    ← 管理端 Vue 3 + Vite + Element Plus
└── docs/
    ├── 数据库设计.md
    ├── 接口文档.md
    ├── 测试用例.md
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

### 4.6 前端硬性规则（F1–F14）

| 编号 | 规则 | 违反后果 |
| :-- | :-- | :-- |
| **F1** | **前端不参与算钱、不参与判冲突。** 费用一律调 `POST /api/orders/quote`（后端 K3 公式）；占用一律读 `GET /api/items/{id}/calendar` | 前后端两套公式 → 显示价与结算价对不上，答辩必被问倒 |
| **F2** | 枚举**单一来源** `utils/constants.js`，与后端枚举一一对应；页面里禁止出现 `'PENDING'`、`'CONFIRMED'` 字面量 | 后端改枚举值，前端静默错乱 |
| **F3** | **前端权限只做体验，不做安全**：隐藏按钮≠接口安全。所有越权判定必须由后端 `@PreAuthorize` + Service 校验兜底 | 改个 URL 就能看别人订单 |
| **F4** | 统一 `<script setup>` 组合式 API，**禁止 Options API**（`data(){return{}}`、`this.xxx`） | 与 Vue 3 生态割裂，AI 抄 Vue 2 代码 |
| **F5** | Element Plus **按需引入**：`unplugin-auto-import` + `unplugin-vue-components`，不全局 `app.use(ElementPlus)` | 首屏体积翻倍 |
| **F6** | 路由**全部懒加载**：`component: () => import('@/views/...')` | 打包成单文件，首屏 3MB+ |
| **F7** | axios **只有一个实例**（`api/request.js`），拦截器统一处理：`code!==0` 抛错+提示、`401` 清 token 跳登录、`4090` 展示候选时段并高亮日历。页面里不重复写 try-catch 弹 toast | 几十处重复错误处理，错误码无人统一 |
| **F8** | 日期用 `dayjs`，传给后端一律 `'YYYY-MM-DD'` 字符串；金额只读展示，固定两位小数 | 传 `Date` 对象 → 时区错一天；金额被 JS 浮点污染 |
| **F9** | **Long 型 id 一律按字符串处理**（后端已 `ToStringSerializer`），禁止 `Number(id)`、禁止 `==` 比较 id | 超过 2^53 精度丢失，订单查不到 |
| **F10** | Pinia **只放跨页面状态**（登录用户、下单草稿、全局 UI）；页面内部状态用 `ref`/`reactive` | store 变成全局垃圾桶，刷新即错乱 |
| **F11** | 开发期用 `vite.config.js` 的 proxy 把 `/api` 转发到 `http://localhost:8080`，**前端代码里不出现后端域名/IP** | 换环境就要改代码 |
| **F12** | 样式用 `scss` + CSS 变量（`variables.scss`），禁止用内联 `style` 堆业务样式（动态计算除外） | 主题无法统一，改色改一天 |
| **F13** | 要「假数据」只能放 `src/mock/*.js` 并在提交说明里标注**哪几个接口还是 mock**；不许把假数据写进 views 假装功能已完成 | 答辩演示时数据全是假的 |
| **F14** | 前端同样受 §13 AI 准则约束：先说方案再写文件、一次只改点名文件、不新增依赖、不写 TODO 当完成 | — |

### 4.7 页面清单（= 开发排期依据）

**用户端 web-user**

| 页面 | 路由 | 对应需求 | 优先级 |
| :-- | :-- | :-- | :-- |
| 登录 / 注册 | `/login` `/register` | FR-M1-01 | P0 |
| 首页（含推荐位） | `/home` | FR-M5-01~04 | P1 |
| 物品列表 / 搜索 | `/items` | FR-M1-03 | P0 |
| 物品详情（占用日历+试算） | `/items/:id` | FR-M1-04、FR-M2-01 | P0 |
| 下单确认页 | `/orders/create?itemId=` | FR-M1-06、FR-M3-01 | P0 |
| 我的订单（我租的/我租出的 Tab） | `/orders` | FR-M1-07 | P0 |
| 订单详情（状态流转+结算单+申诉） | `/orders/:id` | FR-M1-08、FR-M3-05、FR-M6-04 | P0 |
| 发布物品 | `/items/publish` | FR-M1-02 | P0 |
| 我的物品 | `/items/mine` | FR-M1-09 | P0 |
| 我的信誉分 | `/credit` | FR-M4-01 | P0 |
| 消息中心 | `/messages` | FR-M1-10 | P1 |
| 个人中心 | `/profile` | FR-M1-01 | P1 |

**管理端 web-admin**

| 页面 | 路由 | 对应需求 | 优先级 |
| :-- | :-- | :-- | :-- |
| 登录 | `/login` | — | P0 |
| 物品审核 | `/audit` | FR-M1-11 | P0 |
| 策略配置（费率/信誉/损耗） | `/config` | FR-M3-02、FR-M4-03、FR-M6-03 | P1 |
| 纠纷裁决 | `/appeals` | FR-M6-05 | P1 |
| 数据看板（含逾期扫描按钮） | `/dashboard` | FR-M2-05、FR-M3-06 | P1 |
| 用户管理 | `/users` | — | P2 |

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
    url: jdbc:mysql://localhost:3306/rental?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
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

> **用 Druid 的理由（论文/答辩口径）**：项目负责人指定；相较内置 HikariCP，Druid 自带 **SQL 监控、慢 SQL 统计、连接池实时状态** 与 Web 关联统计，可在答辩时直接打开 `/druid` 页面演示「并发下单时的连接占用与慢 SQL」，作为 K2 并发压测的辅助证据。

---

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

- 建库建表统一 `DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci`。

---

## 7. 接口与响应规范

### 7.1 统一响应体

```java
Result<T> { int code; String message; T data; String traceId; }
```

- 成功固定 `code = 0`，`message = "ok"`。
- Controller **必须**返回 `Result<T>`，禁止直接返回 Entity 或 `Map`。
- 分页统一 `PageResult<T> { List<T> records; long total; int pageNum; int pageSize; }`。

### 7.2 错误码段位（**禁止自定义新段位**）

| 段位 | 领域 | 已知码 |
| :-- | :-- | :-- |
| `0` | 成功 | `0` |
| `1xxx` | 认证/权限 | `1001 UNAUTHORIZED`、`1002 FORBIDDEN`、`1003 TOKEN_EXPIRED` |
| `2xxx` | 参数/业务校验 | `2001 PARAM_INVALID`、`2002 ITEM_NOT_FOUND`、`2003 SELF_RENT_FORBIDDEN` |
| `3xxx` | 状态机/权益 | `3001 INVALID_STATE_TRANSITION`、`3010 CREDIT_TOO_LOW`、`3011 ITEM_IN_MAINTENANCE` |
| `4xxx` | 费用 | `4001 FEE_OUT_OF_RANGE`、`4002 SETTLEMENT_ALREADY_DONE` |
| `4090` | **时段冲突**（单独留号，压测断言依赖它） | `4090 TIME_CONFLICT` |
| `5xxx` | 系统 | `5000 SYSTEM_ERROR`、`5030 LOCK_ACQUIRE_FAILED` |

新增错误码必须先用 `grep` 确认未被占用，并同步更新 `docs/接口文档.md` 与 `ResultCode` 枚举。

### 7.3 分页与参数

- 分页入参固定 `pageNum`（从 1 起）、`pageSize`（默认 10，**上限 100**，超出截断不报错）。
- 所有请求 DTO 必须用 `jakarta.validation` 注解（`@NotNull`、`@Min`、`@Future` 等），Controller 参数加 `@Valid`。
- 禁止把 `HttpServletRequest` 传进 Service。
- 当前登录用户从 `SecurityContextHolder` 或自定义 `@CurrentUser` 注解取，**禁止**从请求参数里信任 `userId`（防越权）。

---

## 8. 异常与事务规范

### 8.1 异常

- 业务异常统一抛 `BizException(ResultCode)`，**禁止** `throw new RuntimeException("xxx")`。
- 全局 `@RestControllerAdvice` 统一转换，**禁止**在 Controller 里 `try-catch` 包一层返回 `Result.error`。
- **禁止**向客户端暴露堆栈、SQL、类名。
- **必须**捕获并转换的特定异常：`DuplicateKeyException` → `4090 TIME_CONFLICT`（占用日唯一索引兜底路径）。

### 8.2 事务

- `@Transactional(rollbackFor = Exception.class)` 加在 **public** 的 Service 实现方法上。
- **禁止**同类内部方法自调用（事务失效）；需要时拆到另一个 Bean。
- **禁止** `private` / `final` / `static` 方法加事务注解。
- 事务内**禁止**做 HTTP 调用、发消息、写 Redis（放到事务提交后，用 `TransactionSynchronization` 或事件）。
- 事务边界只在 Service；Controller 与 Mapper 不加。

### 8.3 并发（K2 专用，不可省略任何一层）

下单落库路径**必须**按此顺序：

```
① 应用层预检：查该物品未终结订单 → 内存区间比对 → 快速拒绝
② 缓存层加锁：Redis SET key val NX PX 10000，key = "lock:item:{itemId}"
             释放必须用 Lua 校验持有者后 DEL（禁止裸 DEL）
③ 数据库层：事务内 INSERT order + 批量 INSERT order_occupancy
            依赖 uk_occupancy(item_id, occupy_date) 兜底，捕获 DuplicateKeyException
```

**禁止的「简化」**（交了就是错误）：

- ❌ 只用 `SELECT ... WHERE` 判断后插入
- ❌ 用 `synchronized` 或本地 `ReentrantLock` 替代（单机假象，且与事务边界冲突）
- ❌ 用 `SELECT ... FOR UPDATE` 但不在事务内
- ❌ 锁 key 用 `lock:order`（粒度太粗）、TTL 不设（死锁）

---

## 9. 配置外置规范（**不许硬编码**）

以下数值**必须**从配置表或 `application.yml` 读取，**禁止**在 Java 代码里出现字面量：

| 类别 | 键 | 默认值 |
| :-- | :-- | :-- |
| 信誉分 | `credit.init` / `.max` / `.min` | 80 / 100 / 0 |
| 信誉分 | `credit.reward.on_time` / `.daily_cap` | +2 / +4 |
| 信誉分 | `credit.penalty.overdue_light` / `.overdue_heavy` / `.damage` / `.cancel_after_confirm` | −5 / −10 / −10 / −3 |
| 信誉档 | 免押 ≥80、正常 60–79、上浮 40–59（×1.5）、暂停 <40 | — |
| 费用 | `fee.overdue.tier1_days` / `.tier1_rate` / `.tier2_rate` | 3 / 0.5 / 1.0 |
| 费用 | `fee.overdue_rate.min` / `.max` | 0.1 / 2.0 |
| 费用 | `fee.daily_price.min` / `.max` | 1 / 500 |
| 损耗 | `loss.threshold.maintain` / `.repair` | 0.6 / 0.85 |
| 损耗 | 租期/类目/归还状态系数 | 见需求总结 §5 |
| 推荐 | `recommend.w.cf` / `.content` / `.heat` | 0.6 / 0.3 / 0.1 |
| 推荐 | `recommend.decay.half_life`（天） | 30 |
| 订单 | `order.lock.ttl`（秒） | 10 |

**实现方式**：策略类配置放 `credit_policy` / `fee_policy` / `loss_policy` 表，启动加载进 `@ConfigurationProperties` Bean，配合 `@RefreshScope` 或手动刷缓存。系统级开关放 `sys_config` 表。

> 验收方式：改一行数据库配置，不重启代码不重新编译，系统行为随即改变。这是「规则外置」的证据，演示时会用到。

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
- 新增核心业务方法（冲突判定、费用计算、信誉变更、损耗累计）**必须**配单元测试。
- 测试命名：`方法名_场景_期望结果`，例如 `createOrder_overlapRange_throwsTimeConflict`。
- 必须覆盖的边界（详见 `docs/测试用例.md`）：
  - `[1,3)` vs `[3,5)` → 不冲突；`[1,3)` vs `[2,4)` → 冲突
  - 逾期正好 3 天 vs 4 天（阶梯分界）
  - 押金 − 逾期费 − 损耗费 < 0 → 待补缴
  - 信誉分 100 封顶 / 0 下限 / 一天多单只加 4 分
  - 结算幂等：连调两次返回同一 `settle_no`
- 并发验证：`CountDownLatch` + 线程池 50 线程打同一时段，断言成功订单数 == 1。

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
| **G1 编译** | `mvn -q clean compile` | 零错误 |
| **G2 单测** | `mvn -q test` | 本次改动相关的测试**全绿**；新增业务方法必须有对应测试 |
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
| `AI协作开发手册.md` | 七步闭环、提示词模板、质量闸门、版本陷阱 |
| `docs/数据库设计.md` | 表结构、索引、DDL（待产出） |
| `docs/接口文档.md` | REST 契约、错误码（待产出） |
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
| 2026-11-10 | 项目负责人 | v1.2：①§2 连接池由 HikariCP 改为 **Druid 1.2.23**（`druid-spring-boot-3-starter`），新增 §4.8 Druid 配置契约 D1–D8；②补齐前端架构 §4.4 两个独立 Vite 应用目录、§4.5 前端分层职责、§4.6 前端硬性规则 F1–F14、§4.7 两端页面清单与 FR 映射 |
