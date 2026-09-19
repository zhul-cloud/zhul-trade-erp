## Context

见 `proposal.md - Why`。技术前提：

- v1.2 商品域在 `.claude/context/data-model.md` 里已列为规划（`product`、`product_spec`、`product_brand`），尚未设计。`llm-wiki` 中有一份 v1.2.0 草案 DDL（`wiki/seo-geo/Product-Master-schema.sql`，实际 9 张表）和一份数据层架构决策（`Product-Master数据层架构决策.md`），本设计在其基础上修正，**以本文为准**。
- 首批数据来自独立站 `fouwell-website/js/data.js`。预干跑（2026-09-19）实测：87 个商品、40 个品牌、6 个品类、80 个不同的（品牌, 系列）；无空型号、无空系列；按本设计的归一化算法同品牌下无型号碰撞；品牌名无近似重复。
- **项目已有"平台级共享"约定**：`schema_v1.sql` 里 3 张表的 `tenant_id` 注释为"租户ID（0=平台级共享）"，`DictTypeServiceImpl` 按"本租户 或 tenant_id=0"读取。
- **后端没有租户拦截器**，各 Service 手动按 `TenantContext` 过滤（如 `CustomerServiceImpl`）；**有权限机制**：`@PreAuthorize("@perm.has('system:dept:add')")`，`PermissionChecker` 对 `adminFlag=1` 全放行，否则按 角色→资源（`type=3` 按钮）判断。**`perm.has` 只看角色，不看租户。** JWT 带 `tenantId` 并写入 `TenantContext`。
- 现有接口约定：路径 `/api/v1/{模块}/{资源}`（如 `/api/v1/masterdata/customers`），分页参数 `page`、`pageSize`（与 `coding.md` 写的 `page_size` 不同，以代码为准），响应统一 `Result<T>`。
- v1.1 表沿用 `bigint(20) AUTO_INCREMENT` 主键、审计四件套、`deleted_at`、无数据库外键。

## Goals / Non-Goals

**Goals：**
- 提供全平台唯一的商品标识，让询盘等模块后续可以关联，而不再各存自由文本
- 把"平台共享"落成可执行的读写规则，避免租户越权修改共享数据
- 型号去重要能识别空格、连字符、大小写、全角等写法差异
- 维护商品的技术资料、应用场景、FAQ，并给 FAQ 设发布门禁：未经人工审核的内容不能被租户读到（决策 12）

**Non-Goals：**
- 不做价格、库存、成色、MOQ、交期（租户级商业数据）与独立站 SEO 发布字段（`seo_slug`/`index_status`/`page_tier`）
- 不把卖家自己的质保、库存、发货、联系方式类 FAQ 放进平台共享的 `product_faq`（决策 12）
- 不改询盘模块：`inquiry_order_item.product_id` 与 `ProductUsageChecker` 的询盘侧实现留给 `link-inquiry-to-product`
- 不切换独立站数据源；`ErpProductRepository` 的对接不在本次范围
- 不做 HS 编码、原产国、重量体积；不做商品图片与技术资料文件的上传实现（`main_image_url`、`file_url` 只存地址）
- 本模块**没有金额字段**，不涉及舍入与汇率规则

## Decisions

### 1. 平台共享 = 保留 `tenant_id` 列，值固定为 0

**决策**：九张表都保留 `tenant_id`，值恒为 0。读取恒带 `tenant_id = 0`（本期不存在租户私有行）；写入时服务端强制写 0，忽略客户端传入。COMMENT 统一为"租户ID（0=平台级共享，本模块所有数据均为0）"。

**理由**：这是项目已有约定（见 Context），与 `dict` 一致；保留列意味着将来若要允许"租户私有商品"，只需放开 `tenant_id>0` 的写入与读取并集，不需要改表结构；所有业务表统一有 `tenant_id` 也符合 `coding.md`。

**备选方案**：像 `resource` 那样去掉 `tenant_id` 列。改动最小，但一旦需要租户私有扩展就要改表；且失去与 `dict` 的一致性。已否决。

### 2. 写权限 = 权限码 + 平台账号，双重校验

**决策**：所有写接口同时满足两个条件：① `@PreAuthorize("@perm.has('product:...')")`；② `PlatformScopeGuard` 要求 `TenantContext.getTenantId() == 0`。读接口只要求登录。

| 权限码 | 覆盖 |
|--------|------|
| `product:brand:add` / `edit` / `delete` | 品牌 新增 / 修改与启停 / 删除 |
| `product:category:add` / `edit` / `delete` | 品类，同上 |
| `product:series:add` / `edit` / `delete` | 系列，同上 |
| `product:product:add` / `edit` / `delete` | 商品 新增 / 修改、启停、恢复、规格、型号关系、技术资料、应用场景、FAQ（含审核）/ 删除 |

**理由**：`perm.has` 只看角色不看租户。租户管理员若能把 `product:*` 分给自己的角色，仅靠 ① 就能修改全平台共享数据。② 用 JWT 里已有的 `tenantId` 判断，不需要新增账号字段。

**备选方案**：(a) 只用权限码：如上，越权。(b) 新建"平台管理员"表或账号字段：多一套概念，且 `tenantId` 本身已经表达了账号归属。(c) 复用 `adminFlag`：它是"超级管理员"，不等于"平台账号"，租户下也可能有 `adminFlag=1` 的账号。均已否决。

**前提（已确认）**：平台账号登录后 JWT 的 `tenantId` 为 0。用户于 2026-09-19 确认；仓库内没有账号种子数据可对照，未在代码中独立核实。`PlatformScopeGuard` 是唯一判定点，识别规则日后若变，只改这一处。

### 3. 商业数据与站点发布字段移出商品主表

**背景**：wiki 草案的 `product` 表里放了 `condition`、`inventory_status`、`stock_quantity`、`price`、`currency_code`、`price_source`、`price_remark`、`price_verified_at`、`moq`、`lead_time_*`，以及 `seo_slug`、`index_status`、`page_tier`。

**决策**：这些字段不放进平台共享的 `product`。`product` 只保留标识、分类、描述、生命周期这类"关于零件本身的事实"。

**已确认**：用户于 2026-09-19 确认价格、库存等移出商品主表。

**理由**：价格、库存、成色、交期是**每个租户各自的**，放进平台共享表等于所有租户共用同一价格和库存，逻辑上不成立。`seo_slug`/`index_status`/`page_tier` 是某个租户独立站的发布决策，slug 也可由消费方按品牌/系列/型号派生。

**代价**：独立站在"租户商品扩展"出现之前拿不到价格（87 条里有 70 条带 `sell_price`）和库存状态，因此**不能完全切换到 ERP 数据源**，继续使用本地 `LocalProductRepository`。品牌、品类、系列、规格、型号关系可以先切。

**备选方案**：(a) 商业字段留在 `product`：违背平台共享。(b) 本期同时建 `product_tenant_offer` 扩展表：范围翻倍，且它涉及价格金额与币种，应与报价中心一并设计。均已否决；扩展表字段沿用草案对应列，金额 `DECIMAL(18,2)` + 币种。

### 4. 型号归一化：去掉所有非字母数字，唯一键含品牌

**决策**：`mpn_normalized = 去首尾空格 → NFKC → toLowerCase(Locale.ROOT) → 去掉所有 [^a-z0-9]`，唯一键 `(tenant_id, brand_id, mpn_normalized)`。归一化为空则拒绝。

| 输入 | 输出 |
|------|------|
| `6ES7 214-1BD23-0XB0` / `6ES7214-1BD23-0XB0` / 首尾带空格 | `6es72141bd230xb0`（已用等价 JS 实测三者相同） |
| 全角 `６ＥＳ７２１４` | `6es7214`（NFKC，由单元测试验证） |
| `SGMAH-04ADA-TF13` | `sgmah04adatf13` |
| `---` | 空串，拒绝 |

**理由**：wiki 草案要求 `mpn_normalized` 与独立站 `_slugPiece()` 一致，但实测 `_slugPiece("6ES7 214-1BD23-0XB0")` 得 `6es7-214-1bd23-0xb0`，而 `_slugPiece("6ES7214-1BD23-0XB0")` 得 `6es7214-1bd23-0xb0`，**空格写法无法合并**，恰好是要解决的问题。草案的唯一键不含品牌，会阻止不同品牌使用相同型号；而 `PartNumber` 提案把重复定义为"同 MPN + 同品牌"。slug 是站点侧派生值，不必与去重键一致。

**备选方案**：保持与 `_slugPiece` 一致并另设去重键——两个键增加维护成本，且 slug 不在本期范围。已否决。

**已接受的风险**：去掉分隔符后理论上 `A-1B` 与 `A1-B` 会碰撞。工控型号里极少，碰撞时以"已存在"阻止，由管理员人工判断，不提供绕过。

### 5. 软删除保留唯一键占位，重复新建走"恢复"

**决策**：唯一键包含已软删除的行，所以已删除商品不能重新新建同一型号，创建接口返回 `PRODUCT_DUPLICATE` 并带 `deleted=true` 和 `existingId`，前端引导调用 `POST /products/{id}/restore`。品牌、品类、系列同理。

**理由**：恢复保留原 ID，历史引用不断链；如果把 `deleted_at` 放进唯一键来允许重建，同一型号会有多个 ID，引用关系会混乱。

**备选方案**：唯一键含 `deleted_at`。允许重建，但破坏"一个型号一个标识"。已否决。

### 6. 引用保护用 `ProductUsageChecker` 扩展点，依赖方向是业务模块 → 商品模块

**决策**：商品模块定义 `ProductUsageChecker { long countUsage(Long productId); }`，各业务模块实现并注册为 Spring Bean，`ProductService` 注入 `List<ProductUsageChecker>` 汇总次数。**本期没有任何实现**（`inquiry_order_item` 还没有 `product_id`），此时恒为 0。

**理由**：商品模块直接查询询盘表会让主数据依赖业务表，方向反了，新增报价、订单时还得回头改商品模块。

**备选方案**：商品模块内写死查询各业务表。否决。

### 7. 生命周期与 `status` 分离，默认"未知"

**决策**：`lifecycle_status`（1-在产、2-现行、3-旧款、4-已停产、5-停产无替代、6-未知）描述零件本身的事实，`status`（0-禁用、1-启用）控制该记录是否可被选用，二者独立。`lifecycle_status` 数据库默认 6。是否有替代型号查 `product_relationship`，不在生命周期里重复建模。

**理由**：独立站现状把库存和生命周期揉进一个 `status` 三值枚举（`instock/legacy/discont`），已造成过 bug。草案默认值为 1（在产），但没有依据时不应断言"在产"，与仓库一贯的"不确定不编造"一致。

### 8. 商品保留 `deleted_at`，虽然 `coding.md` 说"基础表只用 status"

**决策**：九张表都有 `deleted_at`；品牌、品类、系列、商品另有 `status`（规格、型号关系与三类内容表没有启停语义，不设 `status`）。

**理由**：全局规则"任何删除必须软删除"；商品会被业务单据引用，物理删除会断链。`coding.md` 的"基础表（用户、权限、字典、资源）只用 status"针对的是配置类数据，商品主数据更接近被单据引用的业务数据。

### 9. 事务边界与缓存

本次**没有跨模块写入**，事务都在商品模块内：

| 操作 | 事务范围 |
|------|----------|
| 新建商品 + 规格 | 单事务 |
| 整体替换规格 | 单事务：软删旧行再插新行 |
| 创建关系 + 反向关系 | 单事务，同成功同失败 |
| 删除商品 | 单事务：`SELECT ... FOR UPDATE` 锁商品行 → 调用所有 `ProductUsageChecker` → 无引用则软删除。业务模块关联商品时对同一行 `FOR SHARE` 并复核启用状态，避免"刚检查完无引用就被关联"的竞态 |
| 缓存失效 | 写库事务**提交后**再删缓存（`TransactionSynchronization.afterCommit`），与仓库里"AI 任务提交延后到事务提交后"的做法一致 |

缓存只针对读多写少的基础数据（`coding.md` 规则），商品本身不缓存：`zhul:erp:list:0:product_brand`、`zhul:erp:list:0:product_category`，TTL 300s，Cache-Aside；Key 里的 `0` 即平台级 `tenant_id`。

创建商品天然幂等（唯一键兜底，捕获 `DuplicateKeyException` 转 `PRODUCT_DUPLICATE`），不需要额外幂等键。

### 10. 接口与查找规则

路径前缀 `/api/v1/product/`，分页参数 `page`、`pageSize`。

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/brands`、`/categories`、`/series` | 分页列表；`/options` 返回全部启用项（品牌、品类走缓存），系列支持 `brandId` |
| POST / PUT / PATCH `/{id}/status` / DELETE | 同上各资源 | 增改启停删 |
| GET | `/products` | 分页；`keyword`、`brandId`、`categoryId`、`seriesId`、`lifecycleStatus`、`status`；`includeDeleted=true` 仅平台账号 |
| GET | `/products/search?keyword=&limit=` | 选择器 |
| GET | `/products/match?brand=&mpn=` | 匹配，返回 `{exact, candidates}` |
| GET | `/products/{id}` | 详情，含 `usageCount` |
| POST / PUT / PATCH `/{id}/status` / DELETE / POST `/{id}/restore` | `/products` | 增改启停删恢复 |
| GET / PUT | `/products/{id}/specifications` | PUT 为整体替换 |
| GET / POST / PUT / DELETE | `/products/{id}/relationships[/{relId}]` | POST 支持 `createReverse` |
| GET / POST / PUT / DELETE | `/products/{id}/documents[/{itemId}]`、`/applications[/{itemId}]`、`/faqs[/{itemId}]` | 技术资料、应用场景、FAQ 各自增删改（决策 12）；列表按 `sort_order, id`；**FAQ 的读取接口对租户账号服务端过滤 `source=3`** |
| PATCH | `/products/{id}/faqs/{itemId}/approve` | 审核确认待审核 FAQ：`source` 3→2，记录 `reviewed_by`、`reviewed_at`；仅平台账号 |

错误码：`PRODUCT_NOT_FOUND`、`PRODUCT_DUPLICATE`（`detail`: `existingId`、`deleted`）、`PRODUCT_MPN_INVALID`、`PRODUCT_MPN_IMMUTABLE`、`PRODUCT_IN_USE`（`detail.usageCount`）、`PRODUCT_SERIES_MISMATCH`、`PRODUCT_LIFECYCLE_SOURCE_REQUIRED`、`BRAND_DUPLICATE`、`BRAND_IN_USE`、`CATEGORY_DUPLICATE`、`CATEGORY_IN_USE`、`CATEGORY_CODE_IMMUTABLE`、`SERIES_DUPLICATE`、`SERIES_IN_USE`、`RELATIONSHIP_INVALID`、`RELATIONSHIP_DUPLICATE`、`CONTENT_NOT_FOUND`、`CONTENT_DUPLICATE`、`DOCUMENT_URL_INVALID`、`FAQ_NOT_PENDING`、`PLATFORM_ADMIN_REQUIRED`。

**查找细节**（对应 `product-lookup` spec）：
- 关键词先做与 3.4 相同的归一化；**归一化后为空时跳过型号前缀分支**，否则 `LIKE '%'` 会匹配全部商品。此时仍允许按产品名称匹配。
- 型号前缀走 `idx_mpn_normalized`（唯一键以 `brand_id` 开头，覆盖不了"跨品牌按型号前缀"）；产品名称为 `LIKE '%kw%'`，不走索引，目录规模（百到千级）下可接受，规模上量后再评估。
- `LIKE` 关键词需转义 `%`、`_`。
- `match` 的品牌比较沿用品牌唯一性规则（忽略大小写和首尾空格），不做去分隔符处理；品牌不匹配时仍按型号返回候选。
- `limit` 缺省 20，最大 50，非正数按缺省处理；`candidates` 最多 10 条。
- 选择器是高频接口：前端防抖，服务端限制 `limit`，并沿用项目现有限流机制（如有）。

**型号关系细节**（对应 `product` spec）：对称类型（3/4/5/6）且 `createReverse=true` 时，同一事务再写反向关系，两端都必须是目录内商品；类型 1/2 忽略该参数。置信度为"已验证"时 `verified_by` 必填，`verified_at` 由服务端填当前时间。`related_mpn` 归一化后只匹配到目录内**唯一一个**商品时自动填 `related_product_id`，匹配到多个品牌则不填。去重键为应用层的 `(product_id, 关联型号归一化值, relationship_type)`。

### 11. 数据模型

通用约定：主键 `bigint(20) AUTO_INCREMENT`；审计四件套；`deleted_at`；`status`；无数据库外键，一致性由 Service 保证；InnoDB、utf8mb4。品牌名称、系列名称的"忽略大小写"依赖数据库默认的不区分大小写排序规则，**实现时需确认 MySQL 8 实际使用的排序规则**，首尾空格由 Service 去除。

```sql
-- schema_v1.2.sql（依赖 schema_v1.sql、schema_v1.1.sql；不修改任何已有表）
use zhul_erp;

DROP TABLE IF EXISTS `product_brand`;
CREATE TABLE `product_brand`
(
    `id`          bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`   int(11)      NOT NULL DEFAULT 0 COMMENT '租户ID（0=平台级共享，本模块所有数据均为0）',
    `brand_name`  varchar(64)  NOT NULL DEFAULT '' COMMENT '品牌名称，如Siemens/ABB',
    `country`     varchar(64)  NOT NULL DEFAULT '' COMMENT '原产国/地区',
    `logo_url`    varchar(256) NOT NULL DEFAULT '' COMMENT 'Logo图片地址',
    `brand_color` varchar(16)  NOT NULL DEFAULT '' COMMENT '品牌主题色（HEX），独立站展示用',
    `is_genuine`  tinyint(2)   NOT NULL DEFAULT 1 COMMENT '是否原厂正品品牌（0-兼容/非原厂、1-原厂正品）；为0时下游不得对该品牌商品使用"Genuine"类正品断言',
    `status`      tinyint(2)   NOT NULL DEFAULT 1 COMMENT '状态（0-禁用、1-启用）',
    `deleted_at`  datetime     NULL COMMENT '软删除时间，NULL表示未删除',
    `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`   varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`   varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_brand_name` (`tenant_id`, `brand_name`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_status` (`status`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '品牌主数据表';

DROP TABLE IF EXISTS `product_category`;
CREATE TABLE `product_category`
(
    `id`            bigint(20)  NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`     int(11)     NOT NULL DEFAULT 0 COMMENT '租户ID（0=平台级共享，本模块所有数据均为0）',
    `category_code` varchar(32) NOT NULL DEFAULT '' COMMENT '品类编码，小写蛇形，如controllers/servo；独立站URL直接沿用，有商品后不可修改',
    `category_name` varchar(64) NOT NULL DEFAULT '' COMMENT '品类名称，如PLC & Controllers',
    `parent_id`     bigint(20)  NULL COMMENT '上级品类ID，关联product_category.id；当前只有一级品类，预留，暂不使用',
    `sort_order`    int(11)     NOT NULL DEFAULT 0 COMMENT '排序',
    `status`        tinyint(2)  NOT NULL DEFAULT 1 COMMENT '状态（0-禁用、1-启用）',
    `deleted_at`    datetime    NULL COMMENT '软删除时间，NULL表示未删除',
    `create_time`   datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`     varchar(32) NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`   datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`     varchar(32) NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_category_code` (`tenant_id`, `category_code`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_parent_id` (`parent_id`),
    KEY `idx_status` (`status`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '品类主数据表';

DROP TABLE IF EXISTS `product_series`;
CREATE TABLE `product_series`
(
    `id`          bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`   int(11)      NOT NULL DEFAULT 0 COMMENT '租户ID（0=平台级共享，本模块所有数据均为0）',
    `brand_id`    bigint(20)   NOT NULL DEFAULT 0 COMMENT '品牌ID，关联product_brand.id',
    `series_name` varchar(64)  NOT NULL DEFAULT '' COMMENT '系列名称，如SITOP/Sigma-7',
    `description` varchar(500) NOT NULL DEFAULT '' COMMENT '系列简介',
    `status`      tinyint(2)   NOT NULL DEFAULT 1 COMMENT '状态（0-禁用、1-启用）',
    `deleted_at`  datetime     NULL COMMENT '软删除时间，NULL表示未删除',
    `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`   varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`   varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_brand_series` (`tenant_id`, `brand_id`, `series_name`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_brand_id` (`brand_id`),
    KEY `idx_status` (`status`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '产品系列表';

DROP TABLE IF EXISTS `product`;
CREATE TABLE `product`
(
    `id`                bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`         int(11)      NOT NULL DEFAULT 0 COMMENT '租户ID（0=平台级共享，本模块所有数据均为0）',
    `mpn_raw`           varchar(128) NOT NULL DEFAULT '' COMMENT '原始型号（制造商资料/询盘原文，仅去首尾空格，其余不清洗；被引用后不可修改）',
    `mpn_normalized`    varchar(128) NOT NULL DEFAULT '' COMMENT '归一化型号（NFKC后转小写，去掉所有非字母数字字符），用于去重与检索；系统生成，禁止手工编辑',
    `mpn_display`       varchar(128) NOT NULL DEFAULT '' COMMENT '展示型号，页面标题使用；默认等于mpn_raw',
    `brand_id`          bigint(20)   NOT NULL DEFAULT 0 COMMENT '品牌ID，关联product_brand.id；被引用后不可修改',
    `category_id`       bigint(20)   NOT NULL DEFAULT 0 COMMENT '品类ID，关联product_category.id',
    `series_id`         bigint(20)   NULL COMMENT '系列ID，关联product_series.id；必须属于brand_id对应品牌；未分配时为空',
    `product_name`      varchar(128) NOT NULL DEFAULT '' COMMENT '产品名称，如SITOP Power Supply',
    `short_description` varchar(500) NOT NULL DEFAULT '' COMMENT '简介，需能追溯到官方资料或询盘单，不得凭空扩写',
    `spec_summary`      varchar(300) NOT NULL DEFAULT '' COMMENT '一句话核心规格摘要，列表页展示',
    `main_image_url`    varchar(256) NOT NULL DEFAULT '' COMMENT '主图地址',
    `lifecycle_status`  tinyint(2)   NOT NULL DEFAULT 6 COMMENT '生命周期（1-在产Active、2-现行Current、3-旧款Legacy、4-已停产Discontinued、5-停产无替代Obsolete、6-未知Unknown）；是否有替代型号查product_relationship',
    `lifecycle_source`  varchar(128) NOT NULL DEFAULT '' COMMENT '生命周期判断依据；lifecycle_status为4或5时必填',
    `status`            tinyint(2)   NOT NULL DEFAULT 1 COMMENT '状态（0-禁用、1-启用）',
    `deleted_at`        datetime     NULL COMMENT '软删除时间，NULL表示未删除',
    `create_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`         varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`         varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_brand_mpn` (`tenant_id`, `brand_id`, `mpn_normalized`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_mpn_normalized` (`mpn_normalized`),
    KEY `idx_brand_id` (`brand_id`),
    KEY `idx_category_id` (`category_id`),
    KEY `idx_series_id` (`series_id`),
    KEY `idx_lifecycle_status` (`lifecycle_status`),
    KEY `idx_status` (`status`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品主数据表（平台共享，Part Number实体核心表）';

DROP TABLE IF EXISTS `product_specification`;
CREATE TABLE `product_specification`
(
    `id`          bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`   int(11)      NOT NULL DEFAULT 0 COMMENT '租户ID（0=平台级共享，本模块所有数据均为0）',
    `product_id`  bigint(20)   NOT NULL DEFAULT 0 COMMENT '商品ID，关联product.id',
    `spec_key`    varchar(64)  NOT NULL DEFAULT '' COMMENT '规格编码，小写蛇形，如rated_voltage；同一商品内唯一',
    `spec_label`  varchar(64)  NOT NULL DEFAULT '' COMMENT '规格显示名称，如Rated Voltage',
    `spec_value`  varchar(256) NOT NULL DEFAULT '' COMMENT '规格值（展示文本）',
    `spec_unit`   varchar(32)  NOT NULL DEFAULT '' COMMENT '单位，如V DC；与规格值分开存储',
    `source`      varchar(128) NOT NULL DEFAULT '' COMMENT '数据来源，如Manufacturer Datasheet/询盘单',
    `verified`    tinyint(2)   NOT NULL DEFAULT 0 COMMENT '是否已核实（0-未核实、1-已核实）',
    `sort_order`  int(11)      NOT NULL DEFAULT 0 COMMENT '排序',
    `deleted_at`  datetime     NULL COMMENT '软删除时间，NULL表示未删除',
    `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`   varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`   varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_product_spec_key` (`product_id`, `spec_key`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_product_id` (`product_id`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品规格参数表';

DROP TABLE IF EXISTS `product_relationship`;
CREATE TABLE `product_relationship`
(
    `id`                 bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`          int(11)      NOT NULL DEFAULT 0 COMMENT '租户ID（0=平台级共享，本模块所有数据均为0）',
    `product_id`         bigint(20)   NOT NULL DEFAULT 0 COMMENT '商品ID（关系挂在哪个商品下），关联product.id',
    `related_mpn`        varchar(128) NOT NULL DEFAULT '' COMMENT '关联型号原文，可以是目录里没有的旧型号/停产型号',
    `related_product_id` bigint(20)   NULL COMMENT '关联商品ID，关联product.id；仅当关联型号在目录内时有值',
    `relationship_type`  tinyint(2)   NOT NULL DEFAULT 6 COMMENT '关系类型（1-官方直接替代、2-厂商后续型号、3-功能性替代、4-兼容、5-交叉引用、6-同系列）；3/4/5/6为对称类型，1/2非对称',
    `confidence`         tinyint(2)   NOT NULL DEFAULT 3 COMMENT '置信度（1-已验证、2-高、3-中、4-低、5-未知）；4/5时下游不得展示为"推荐替代"类强断言',
    `note`               varchar(500) NOT NULL DEFAULT '' COMMENT '关系说明文案，措辞需按relationship_type区分',
    `verified_by`        varchar(32)  NOT NULL DEFAULT '' COMMENT '核实人；confidence=1时必填',
    `verified_at`        datetime     NULL COMMENT '核实时间',
    `sort_order`         int(11)      NOT NULL DEFAULT 0 COMMENT '排序',
    `deleted_at`         datetime     NULL COMMENT '软删除时间，NULL表示未删除',
    `create_time`        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`          varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`          varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_product_id` (`product_id`),
    KEY `idx_related_product_id` (`related_product_id`),
    KEY `idx_relationship_type` (`relationship_type`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品型号关系表（替代/兼容/交叉引用）';

DROP TABLE IF EXISTS `product_document`;
CREATE TABLE `product_document`
(
    `id`            bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`     int(11)      NOT NULL DEFAULT 0 COMMENT '租户ID（0=平台级共享，本模块所有数据均为0）',
    `product_id`    bigint(20)   NOT NULL DEFAULT 0 COMMENT '商品ID，关联product.id；资料通过商品ID强绑定，只属于一个商品',
    `document_type` tinyint(2)   NOT NULL DEFAULT 1 COMMENT '文档类型（1-Datasheet、2-Manual、3-Installation Guide、4-User Manual、5-CAD、6-Drawing、7-Brochure、8-Certificate）',
    `title`         varchar(128) NOT NULL DEFAULT '' COMMENT '文档标题',
    `file_url`      varchar(512) NOT NULL DEFAULT '' COMMENT '文件地址，仅允许http://、https://或以单个/开头的站内路径；本模块只存地址，不存文件本体',
    `language`      varchar(8)   NOT NULL DEFAULT 'en' COMMENT '语言，如en/zh/ru',
    `version`       varchar(32)  NOT NULL DEFAULT '' COMMENT '文档版本',
    `source`        varchar(128) NOT NULL DEFAULT '' COMMENT '数据来源，如Manufacturer Website',
    `verified`      tinyint(2)   NOT NULL DEFAULT 0 COMMENT '是否已核实（0-未核实、1-已核实）；仅为标记，不影响读取',
    `verified_at`   datetime     NULL COMMENT '核实时间；verified=1时由服务端填写',
    `sort_order`    int(11)      NOT NULL DEFAULT 0 COMMENT '排序',
    `deleted_at`    datetime     NULL COMMENT '软删除时间，NULL表示未删除',
    `create_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`     varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`     varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_product_id` (`product_id`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品技术资料表';

DROP TABLE IF EXISTS `product_application`;
CREATE TABLE `product_application`
(
    `id`          bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`   int(11)      NOT NULL DEFAULT 0 COMMENT '租户ID（0=平台级共享，本模块所有数据均为0）',
    `product_id`  bigint(20)   NOT NULL DEFAULT 0 COMMENT '商品ID，关联product.id',
    `title`       varchar(64)  NOT NULL DEFAULT '' COMMENT '应用场景标题，如Water & pump stations',
    `description` varchar(500) NOT NULL DEFAULT '' COMMENT '场景说明，必须基于真实产品用途，不得为SEO编造',
    `icon`        varchar(16)  NOT NULL DEFAULT '' COMMENT '图标，直接存emoji（迁移自独立站）或图标标识；展示方式由消费方决定',
    `verified`    tinyint(2)   NOT NULL DEFAULT 0 COMMENT '是否已人工核实（0-未核实、1-已核实）；仅为标记，不影响读取',
    `sort_order`  int(11)      NOT NULL DEFAULT 0 COMMENT '排序',
    `deleted_at`  datetime     NULL COMMENT '软删除时间，NULL表示未删除',
    `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`   varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`   varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_product_id` (`product_id`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品应用场景表';

DROP TABLE IF EXISTS `product_faq`;
CREATE TABLE `product_faq`
(
    `id`          bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`   int(11)      NOT NULL DEFAULT 0 COMMENT '租户ID（0=平台级共享，本模块所有数据均为0）',
    `product_id`  bigint(20)   NOT NULL DEFAULT 0 COMMENT '商品ID，关联product.id',
    `question`    varchar(256) NOT NULL DEFAULT '' COMMENT '问题（纯文本）',
    `answer`      text         NOT NULL COMMENT '答案（纯文本）；只描述商品本身，不含卖家自己的质保/库存/发货/联系方式承诺',
    `source`      tinyint(2)   NOT NULL DEFAULT 3 COMMENT '来源（1-品类通用模板生成、2-人工撰写或已人工审核确认、3-AI辅助生成待审核）；3的记录不对租户账号返回，需平台账号审核后改为2',
    `reviewed_by` varchar(32)  NOT NULL DEFAULT '' COMMENT '审核人；由待审核确认为2时填写',
    `reviewed_at` datetime     NULL COMMENT '审核时间',
    `sort_order`  int(11)      NOT NULL DEFAULT 0 COMMENT '排序',
    `deleted_at`  datetime     NULL COMMENT '软删除时间，NULL表示未删除',
    `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`   varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`   varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_product_id` (`product_id`),
    KEY `idx_source` (`source`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品FAQ表';
```

### 12. 技术资料、应用场景、FAQ 并入本 change

**背景**：原计划作为 M2 另起 `add-product-master-content`（v1.2.1）。用户于 2026-09-19 要求一并设计，因此九张表同进 `schema_v1.2.sql`，随 v1.2.0 交付；实现顺序上内容类任务排在商品主体之后（tasks 5.12 起），商品主体完成即可支撑询盘关联。

**决策**：

1. **归属**：三张表都挂在商品下，`product_id` 强绑定，一份资料只属于一个商品。平台共享（`tenant_id=0`）、软删除、不设 `status`；随商品一起被删除和恢复，与规格、型号关系一致。
2. **无数据库唯一键，应用层查重**：文档按 `file_url`、应用场景按 `title`、FAQ 按 `question` 在同一商品的**未删除**行内查重（标题与问题忽略大小写和首尾空格）。迁移用 `NOT EXISTS` 保持幂等。
   - 理由：内容行随编辑改动，若唯一键含已删除行会像决策 5 一样阻止重建，而这些行没有引用需要保护。实测 87 个商品内无重复问题、无重复标题。
   - 代价：并发下理论上可能写出重复行。写入仅限平台管理员，频率低，可接受。
3. **FAQ 发布门禁**：`source=3`（AI 辅助生成待审核）的 FAQ **只对平台账号可见**，租户账号读取时由服务端过滤，不靠前端。管理员在后台手动新增的 FAQ，`source` 恒为 2；`1`、`3` 只来自迁移或批量导入。`PATCH .../approve` 把 3 改为 2 并写入 `reviewed_by`、`reviewed_at`，其余来源调用返回 `FAQ_NOT_PENDING`。修改 FAQ 内容不改变 `source`，审核是单独动作。
4. **卖家承诺不进共享表**：实测 `js/data.js` 的 FAQ 里夹带了卖家自己的承诺（如"Every Fouwell-supplied Siemens part is 100% genuine… 12-month replacement warranty"、"Is this in stock and how fast can it ship?"、`info@fouwell.com`），至少 21 条明确出现 Fouwell。与决策 3 同理：商品表全租户共享，某一家的质保、库存、联系方式放进去，其他租户也会读到。因此 `product_faq` 只放"关于零件本身的事实"（订单号含义、兼容性、与同系列型号的比较等）；卖家类问答属于站点或「租户商品扩展」，迁移时不导入（见 Migration Plan）。
5. **文件地址与文本安全**：`file_url` 只允许 `http://`、`https://`，或以**单个** `/` 开头的站内路径；`//host/...`（协议相对地址）、`javascript:`、`data:` 一律拒绝（返回 `DOCUMENT_URL_INVALID`）。标题、问题、答案、场景说明都按纯文本保存，不解析 HTML，由消费方转义。本模块只存地址，不接收文件上传。
6. **`verified` 只是标记**：技术资料和应用场景的 `verified` 不影响读取，只有 FAQ 有读取门禁。规格参数的 `verified` 也是同样处理。
7. **权限**：沿用 `product:product:edit`（新增、修改、删除、审核都算编辑）+ `PlatformScopeGuard`，不新增权限码；读接口对任意租户登录用户开放（FAQ 受第 3 条限制）。

**备选方案**：(a) 仍另起 change：已被用户否决。(b) 用独立的 `review_status` 与 `source` 分离，`source` 只表示出处：语义更干净，但 wiki 里 `mapErpFaqToSiteFaq()` 已按 `source=3` 过滤、PRD 也按此定义，改动面更大。**已接受的代价**：批准后 `source` 变 2，丢失"曾由 AI 起草"的出处，只保留 `reviewed_by`/`reviewed_at` 作为审核痕迹。(c) 把卖家类 FAQ 也导入并靠审核删除：审核疏漏一次，其他租户就会读到某家的质保和邮箱。已否决。

## Risks / Trade-offs

- **[风险] 平台账号 `tenantId=0` 的前提未在代码中独立核实**（用户已确认，但仓库无种子账号可对照）→ 实现时用集成测试固化该约定；`PlatformScopeGuard` 是唯一判定点，识别规则变化只改这一处。
- **[风险] 独立站不能完全切换到 ERP 数据源**（决策 3）→ 保持 `LocalProductRepository`；同步更新 wiki 决策文档里 `ErpProductRepository` 的字段映射；租户商品扩展另起 change。
- **[风险] 归一化碰撞**（`A-1B` 与 `A1-B`）→ 以"已存在"阻止并由人工判断；预干跑 87 条无碰撞。
- **[风险] 平台共享意味着一处改动影响所有租户** → 写入仅限平台账号；停用、删除有二次确认；被引用的商品不可删除、不可改型号。
- **[风险] 迁移的 FAQ、应用场景来源无法判断**（`js/data.js` 无来源标记，wiki 日志记录为按品类分批撰写）→ FAQ 全部 `source=3` 待审核（Q7）、应用场景 `verified=0`；租户在人工审核前读不到待审核 FAQ。
- **[风险] FAQ 夹带卖家承诺**（决策 12）→ 干跑按关键词标出并**不导入**；关键词会有误伤和漏网，所以干跑输出完整清单供人工复核，验收要求导入结果中不含 `Fouwell`。
- **[风险] 唯一一份技术资料的文件不在仓库里**（`/datasheets/6ES7212-1AE40-0XB0.pdf` 是独立站相对路径，`fouwell-website/` 下没有 `datasheets/` 目录）→ 原样迁入并 `verified=0`，干跑报告；文件由独立站部署提供，本 change 不做上传。
- **[权衡] 内容表没有唯一键** → 应用层查重，并发下可能重复，写入仅限平台管理员，可接受（决策 12）。
- **[权衡] 唯一键含已软删除行** → 已删除的型号只能恢复，不能重建；换来标识稳定。
- **[权衡] 产品名称搜索为 `LIKE '%kw%'`，不走索引** → 目录规模下可接受，上量后再评估全文索引。
- **[权衡] 一次交付九张表** → 范围比只做六张表大；靠 tasks 顺序（内容类任务排在商品主体之后）保持可分段合并，商品主体完成即可支撑询盘关联。

## Migration Plan

全部为新增表和新增模块，不修改任何已有表，风险面小。

1. 执行 `schema_v1.2.sql`（九张表）；回滚仅需 `DROP` 这九张表
2. `resource` 表新增菜单与按钮资源（`type=3`）
3. 迁移脚本（Node，读取 `fouwell-website/js/data.js`），先**干跑**，再导入，生成幂等 SQL（`INSERT ... ON DUPLICATE KEY UPDATE`）。所有迁移行 `create_by='migration'`，回滚：`UPDATE ... SET deleted_at=NOW() WHERE create_by='migration'`。内容类三张表用 `INSERT ... SELECT ... WHERE NOT EXISTS`（同商品下同文件地址 / 标题 / 问题）保持幂等。干跑必须报告（不得静默合并或静默丢弃）：同品牌下归一化型号碰撞、品牌名近似重复、空型号、缺品类、**FAQ 中疑似卖家承诺的条目（完整清单与命中的关键词）**、技术资料文件是否存在

| `js/data.js` | ERP | 规则 |
|--------------|-----|------|
| `brand` | `product_brand.brand_name` | 去重后 40 个；国家/Logo/主题色/`is_genuine` 取自独立站品牌数据与 `NON_GENUINE_BRANDS` |
| `cat` | `product_category.category_code` | 6 个 key 原样使用（`controllers/drives/servo/sensors/hmi/spares`）；名称取自独立站品类名称表 |
| `series` | `product_series` | 按（品牌, 系列）去重创建，预期 80 个 |
| `model` | `mpn_raw` / `mpn_display` | 原样；`mpn_normalized` 由归一化函数生成 |
| `spec` | `spec_summary` | |
| `status` | `lifecycle_status` | `instock`→1、`legacy`→3、`discont`→4；带 `no_known_replacement` 的 1 条→5；`lifecycle_source` 写"迁移自独立站 status=xxx，未逐条核实" |
| `specs`（84 条，`[标签, 值]`） | `product_specification` | `spec_label`=标签，`spec_value`=值，`spec_unit` 留空（值里自带单位，无法可靠拆分），`spec_key`=标签转小写蛇形（同商品内重名加 `_2`），`verified=0`，`source`=独立站迁移 |
| `photo` | `main_image_url` | 由独立站资源路径拼接，规则迁移时确认 |
| `compatibility`（12 条） | `product_relationship` | **子字段结构未检视**：已含 `relationship_type` 则按其映射，否则默认类型 5、置信度 3 |
| `sell_price*`、`price_source`、`condition_note*`、`linkedin` | 不迁移 | 属租户级/站点级数据（决策 3） |
| `datasheet`（仅 1 个商品，字符串路径） | `product_document` | `document_type=1`、`title`=`Datasheet`、`file_url`=原路径、`language`=`en`、`verified=0`、`source`=独立站迁移；文件不在仓库，干跑报告 |
| `applications`（84 个商品共 117 条，`{icon, title, desc}`） | `product_application` | `title`、`description`=`desc`、`icon`=`icon`（emoji 原样，29 种）、`sort_order`=数组顺序、`verified=0` |
| `faq`（84 个商品共 222 条，`{q, a}`） | `product_faq` | `question`=`q`、`answer`=`a`、`source=3`（Q7）、`sort_order`=数组顺序；**命中卖家承诺关键词的条目不导入**（决策 12）。关键词范围：品牌名 `Fouwell`、邮箱与电话、质保 `warranty`、库存与发货 `in stock` / `ship` / `lead time` / `MOQ`、价格 `price` / `quote`、第一人称 `we` / `our`；以干跑清单人工复核为准，误伤的条目由人工加入放行清单后再导入 |

验收：商品 87 / 品牌 40 / 品类 6 / 系列 80，无唯一键冲突；技术资料 1 条；应用场景 117 条（84 个商品）；FAQ = 222 − 干跑排除数（排除数以干跑清单为准），全部 `source=3`，且导入结果中没有任何一条含 `Fouwell`；抽查 5 条商品，规格与独立站页面一致。

## Open Questions

- ~~**Q2（上线门槛）平台账号 JWT 的 `tenantId` 是否为 0？**~~ **已确认（2026-09-19）：是 0。** 见决策 2 的前提。
- Q1 的前半（是否同意把价格、库存等移出商品主表）**已确认同意**（决策 3）。**剩余**：「租户商品扩展」（价格、库存、成色、MOQ、交期、站点发布配置）放在 v1.2.1、v2.0 报价中心还是 v2.2 仓储域？决定独立站何时能切换到 ERP 数据源，不影响本 change。
- Q3：租户用户能否提交新型号？（默认只有平台账号能建商品；租户用户在询盘里选已有商品或留空。）
- Q4：品类是否需要中文名？（默认只有英文名，与独立站 URL 编码一致。）
- Q5：商品是否需要"前缀+主键"业务编码？（默认不需要，MPN 本身即业务标识。）
- Q6：计量单位是否单独做字典？（默认本期不做。）
- Q7：迁移的 FAQ（**84 个商品共 222 条**，不是 84 条）是否视为已人工审核？（默认保守置为 `source=3` 待审核；卖家承诺类条目已决定不导入，见决策 12。）
- Q11：被排除的卖家类 FAQ 与站点内容放在哪里？（默认只保留在干跑报告与独立站本地 `js/data.js`，不改独立站；「租户商品扩展」落地时再迁移。）
- Q8：HS 编码、原产国、重量体积是否预留？（默认不加，出现报关需求再补。）
- Q9：独立站构建机能否访问 ERP API？（wiki 遗留问题；若 ERP 在内网，"实时调用"方案不成立。）
- Q10：询盘明细 `category` 是 `varchar(32)` 自由文本，如何映射到 `category_code`？（默认关联商品时以商品的品类为准，历史明细不回填；属 `link-inquiry-to-product`。）
- wiki 记录商品数为 84、实测 87：**已查清**，84 是 2026-09-18 全站深度页升级时的独立站SKU数，同日又发布 3 个 WECON 型号（84→87），与预干跑一致；`compatibility` 子字段结构未检视；`resource` 表种子数据的具体写法未找到样例。
