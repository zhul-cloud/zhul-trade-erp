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
- 维护商品的图片与视频（支持上传）、物流信息、海关信息和平台共享参考价（决策 13）

**Non-Goals：**
- 不做各租户自己的采购价、售价、库存、成色、MOQ、交期（租户级商业数据）与独立站 SEO 发布字段（`seo_slug`/`index_status`/`page_tier`）；平台共享的**参考价**在范围内（决策 13）
- 不把卖家自己的质保、库存、发货、联系方式类 FAQ 放进平台共享的 `product_faq`（决策 12）
- 不改询盘模块：`inquiry_order_item.product_id` 与 `ProductUsageChecker` 的询盘侧实现留给 `link-inquiry-to-product`
- 不切换独立站数据源；`ErpProductRepository` 的对接不在本次范围
- 不做技术资料文件的上传（`file_url` 只存地址）；图片与视频支持上传（决策 13）
- 本模块**没有金额字段**，不涉及舍入与汇率规则

## Decisions

### 1. 平台共享 = 保留 `tenant_id` 列，值固定为 0

**决策**：十三张表都保留 `tenant_id`，值恒为 0。读取恒带 `tenant_id = 0`（本期不存在租户私有行）；写入时服务端强制写 0，忽略客户端传入。COMMENT 统一为"租户ID（0=平台级共享，本模块所有数据均为0）"。

**理由**：这是项目已有约定（见 Context），与 `dict` 一致；保留列意味着将来若要允许"租户私有商品"，只需放开 `tenant_id>0` 的写入与读取并集，不需要改表结构；所有业务表统一有 `tenant_id` 也符合 `coding.md`。

**备选方案**：像 `resource` 那样去掉 `tenant_id` 列。改动最小，但一旦需要租户私有扩展就要改表；且失去与 `dict` 的一致性。已否决。

### 2. 写权限 = 权限码 + 平台账号，双重校验

**决策**：所有写接口同时满足两个条件：① `@PreAuthorize("@perm.has('product:...')")`；② `PlatformScopeGuard` 要求 `TenantContext.getTenantId() == 0`。读接口只要求登录。

| 权限码 | 覆盖 |
|--------|------|
| `product:brand:add` / `edit` / `delete` | 品牌 新增 / 修改与启停 / 删除 |
| `product:category:add` / `edit` / `delete` | 品类，同上 |
| `product:series:add` / `edit` / `delete` | 系列，同上 |
| `product:product:add` / `edit` / `delete` | 商品 新增 / 修改、启停、恢复、规格、型号关系、技术资料、应用场景、FAQ（含审核）、图片视频（含上传、设主图）、物流、海关、参考价 / 删除 |

**理由**：`perm.has` 只看角色不看租户。租户管理员若能把 `product:*` 分给自己的角色，仅靠 ① 就能修改全平台共享数据。② 用 JWT 里已有的 `tenantId` 判断，不需要新增账号字段。

**备选方案**：(a) 只用权限码：如上，越权。(b) 新建"平台管理员"表或账号字段：多一套概念，且 `tenantId` 本身已经表达了账号归属。(c) 复用 `adminFlag`：它是"超级管理员"，不等于"平台账号"，租户下也可能有 `adminFlag=1` 的账号。均已否决。

**前提（已确认）**：平台账号登录后 JWT 的 `tenantId` 为 0。用户于 2026-09-19 确认；仓库内没有账号种子数据可对照，未在代码中独立核实。`PlatformScopeGuard` 是唯一判定点，识别规则日后若变，只改这一处。

### 3. 商业数据与站点发布字段移出商品主表

**背景**：wiki 草案的 `product` 表里放了 `condition`、`inventory_status`、`stock_quantity`、`price`、`currency_code`、`price_source`、`price_remark`、`price_verified_at`、`moq`、`lead_time_*`，以及 `seo_slug`、`index_status`、`page_tier`。

**决策**：这些字段不放进平台共享的 `product`。`product` 只保留标识、分类、描述、生命周期这类"关于零件本身的事实"。

**已确认**：用户于 2026-09-19 确认价格、库存等移出商品主表。

**修订（2026-09-19，见决策 13）**：用户随后要求商品主数据包含价格信息，选择**平台共享的参考价**，另建 `product_reference_price`，不放进 `product`。本决策里的"价格"指**各租户自己的采购价和售价**，它们仍不放进共享表，仍留给「租户商品扩展」。

**理由**：价格、库存、成色、交期是**每个租户各自的**，放进平台共享表等于所有租户共用同一价格和库存，逻辑上不成立。`seo_slug`/`index_status`/`page_tier` 是某个租户独立站的发布决策，slug 也可由消费方按品牌/系列/型号派生。

**代价**：独立站在"租户商品扩展"出现之前拿不到自己的售价和库存状态（87 条里 70 条带 `sell_price`，其中 56 条是外部参考价、可作为平台参考价迁入，14 条是采购报价、不能），因此**不能完全切换到 ERP 数据源**，继续使用本地 `LocalProductRepository`。品牌、品类、系列、规格、型号关系可以先切。

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

**决策**：十三张表都有 `deleted_at`；品牌、品类、系列、商品另有 `status`（规格、型号关系与其余子表没有启停语义，不设 `status`）。

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
| GET | `/products` | 分页；`keyword`、`brandId`、`categoryId`、`seriesId`、`lifecycleStatus`、`status`；`includeDeleted=true` 与 `missing=media\|logistics\|customs\|price` 仅平台账号；平台账号的结果带 `completeness`（决策 14） |
| GET | `/products/completeness-summary` | 各缺项的商品数（缺图片 / 缺物流 / 缺海关 / 缺参考价），仅平台账号，供列表提示条使用 |
| GET | `/products/search?keyword=&limit=&brandId=` | 选择器；可选 `brandId` 限定品牌，供向导第 1 步提示相近型号 |
| GET | `/products/match?brand=&mpn=` | 匹配，返回 `{exact, candidates}` |
| GET | `/products/{id}` | 详情，含 `usageCount`；平台账号另含 `completeness` |
| POST / PUT / PATCH `/{id}/status` / DELETE / POST `/{id}/restore` | `/products` | 增改启停删恢复 |
| GET / PUT | `/products/{id}/specifications` | PUT 为整体替换 |
| GET / POST / PUT / DELETE | `/products/{id}/relationships[/{relId}]` | POST 支持 `createReverse` |
| GET / POST / PUT / DELETE | `/products/{id}/documents[/{itemId}]`、`/applications[/{itemId}]`、`/faqs[/{itemId}]` | 技术资料、应用场景、FAQ 各自增删改（决策 12）；列表按 `sort_order, id`；**FAQ 的读取接口对租户账号服务端过滤 `source=3`** |
| GET / POST / PUT / DELETE | `/products/{id}/media[/{itemId}]` | 图片与视频：登记外链、改标题与排序、删除；列表按 `is_main desc, sort_order, id`（决策 13） |
| POST | `/products/{id}/media/upload` | multipart 上传一个文件（`file`、`mediaType`），成功返回新建的媒体行；**加限流** |
| PATCH | `/products/{id}/media/{itemId}/main` | 设为主图（仅图片；同一事务取消原主图） |
| GET / PUT | `/products/{id}/logistics`、`/customs` | 一对一，PUT 为整体保存；没有记录时 GET 返回空对象 |
| GET / PUT / DELETE | `/products/{id}/reference-price` | 一对一；PUT 保存并计算本位币金额；DELETE 清除 |
| PATCH | `/products/{id}/faqs/{itemId}/approve` | 审核确认待审核 FAQ：`source` 3→2，记录 `reviewed_by`、`reviewed_at`；仅平台账号 |

**错误响应格式**：沿用现有约定 `{code: number, message, data}`，**数字 `code` 与 `message` 不变**（前端依赖 `code !== 0` 判错）；字符串错误码和 `detail` 放在 `data` 里：`{"code": 500, "message": "该型号已存在", "data": {"errorCode": "PRODUCT_DUPLICATE", "detail": {"existingId": 12, "deleted": false}}}`。这与 `coding.md` 里 `{"code": "ORDER_NOT_FOUND"}` 的写法不同，是为了不破坏现有前后端契约；`BizException` 增加可选的 `errorCode` 和 `detail`（任务 0.3）。

错误码：`PRODUCT_NOT_FOUND`、`PRODUCT_DUPLICATE`（`detail`: `existingId`、`deleted`）、`PRODUCT_MPN_INVALID`、`PRODUCT_MPN_IMMUTABLE`、`PRODUCT_IN_USE`（`detail.usageCount`）、`PRODUCT_SERIES_MISMATCH`、`PRODUCT_LIFECYCLE_SOURCE_REQUIRED`、`BRAND_NOT_FOUND`、`BRAND_DUPLICATE`、`BRAND_IN_USE`、`CATEGORY_NOT_FOUND`、`CATEGORY_DUPLICATE`、`CATEGORY_IN_USE`、`CATEGORY_CODE_IMMUTABLE`、`SERIES_NOT_FOUND`、`SERIES_DUPLICATE`、`SERIES_IN_USE`、`RELATIONSHIP_INVALID`、`RELATIONSHIP_DUPLICATE`、`CONTENT_NOT_FOUND`、`CONTENT_DUPLICATE`、`DOCUMENT_URL_INVALID`、`FAQ_NOT_PENDING`、`MEDIA_URL_INVALID`、`MEDIA_FILE_INVALID`、`MEDIA_TOO_LARGE`、`MEDIA_NOT_IMAGE`、`LOGISTICS_INVALID`、`HS_CODE_INVALID`、`COUNTRY_CODE_INVALID`、`PRICE_INVALID`、`CURRENCY_REQUIRED`、`PLATFORM_ADMIN_REQUIRED`、`PARAM_INVALID`（必填 / 长度 / 格式等通用校验，具体原因在 `message`）。同名记录已被软删除时，品牌、品类、系列的 `*_DUPLICATE` 同样带 `detail.deleted=true`，但本期没有品牌 / 品类 / 系列的恢复接口，只提示"曾被删除，不能重复创建"。

**查找细节**（对应 `product-lookup` spec）：
- 关键词先做与 3.4 相同的归一化；**归一化后为空时跳过型号前缀分支**，否则 `LIKE '%'` 会匹配全部商品。此时仍允许按产品名称匹配。
- 型号前缀走 `idx_mpn_normalized`（唯一键以 `brand_id` 开头，覆盖不了"跨品牌按型号前缀"）；产品名称为 `LIKE '%kw%'`，不走索引，目录规模（百到千级）下可接受，规模上量后再评估。
- `LIKE` 关键词需转义 `%`、`_`。
- `match` 的品牌比较沿用品牌唯一性规则（忽略大小写和首尾空格），不做去分隔符处理；品牌不匹配时仍按型号返回候选。
- `limit` 缺省 20，最大 50，非正数按缺省处理；`candidates` 最多 10 条。
- 选择器是高频接口：前端防抖，服务端限制 `limit`，并沿用项目现有限流机制（如有）。

**型号关系细节**（对应 `product` spec）：对称类型（3/4/5/6）且 `createReverse=true` 时，同一事务再写反向关系，两端都必须是目录内商品；类型 1/2 忽略该参数。置信度为"已验证"时 `verified_by` 必填，`verified_at` 由服务端填当前时间。`related_mpn` 归一化后只匹配到目录内**唯一一个**商品时自动填 `related_product_id`，匹配到多个品牌则不填。去重键为应用层的 `(product_id, 关联型号归一化值, relationship_type)`。

### 11. 数据模型

通用约定：主键 `bigint(20) AUTO_INCREMENT`；审计四件套；`deleted_at`；`status`；无数据库外键，一致性由 Service 保证；InnoDB、utf8mb4。`product_specification` 的 `(product_id, spec_key)` 只建普通索引、不建唯一键：规格是"整体替换"（软删旧行再插新行），唯一键会让相同规格编码无法重新插入，同一商品内规格编码不重复由 Service 校验并靠锁商品行串行化。品牌名称、系列名称的"忽略大小写"依赖数据库默认的排序规则。**已于 2026-09-19 在本机 MySQL 8 实测**：表默认排序规则为 `utf8mb4_0900_ai_ci`，`Siemens` 与 `siemens`、`S7-1200` 与 `s7-1200`、商品归一化型号的大小写变体都会触发唯一键冲突；但该规则不补齐尾部空格，`Siemens ` 与 `Siemens` 会被当成两个值，所以**首尾空格必须由 Service 在写入前去除**，不能指望数据库。

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
    KEY `idx_product_spec_key` (`product_id`, `spec_key`),
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

DROP TABLE IF EXISTS `product_media`;
CREATE TABLE `product_media`
(
    `id`           bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`    int(11)      NOT NULL DEFAULT 0 COMMENT '租户ID（0=平台级共享，本模块所有数据均为0）',
    `product_id`   bigint(20)   NOT NULL DEFAULT 0 COMMENT '商品ID，关联product.id',
    `media_type`   tinyint(2)   NOT NULL DEFAULT 1 COMMENT '媒体类型（1-图片、2-视频）',
    `file_url`     varchar(512) NOT NULL DEFAULT '' COMMENT '文件地址：上传后为站内路径（/uploads/product/...），外链为http(s)地址；不允许其他协议',
    `storage_type` tinyint(2)   NOT NULL DEFAULT 1 COMMENT '存储方式（1-平台上传、2-外部链接）',
    `cover_url`    varchar(512) NOT NULL DEFAULT '' COMMENT '视频封面地址，仅视频使用，地址规则同file_url',
    `title`        varchar(128) NOT NULL DEFAULT '' COMMENT '标题；图片时同时作为替代文字（无障碍与SEO用）',
    `file_size`    bigint(20)   NOT NULL DEFAULT 0 COMMENT '文件大小（字节），仅上传时记录，外链为0',
    `is_main`      tinyint(2)   NOT NULL DEFAULT 0 COMMENT '是否主图（0-否、1-是）；仅图片可为1，同一商品未删除行内最多一张',
    `source`       varchar(128) NOT NULL DEFAULT '' COMMENT '来源，如 Manufacturer Website',
    `sort_order`   int(11)      NOT NULL DEFAULT 0 COMMENT '排序',
    `deleted_at`   datetime     NULL COMMENT '软删除时间，NULL表示未删除',
    `create_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`    varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`    varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_product_id` (`product_id`),
    KEY `idx_product_main` (`product_id`, `is_main`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品图片与视频表';

DROP TABLE IF EXISTS `product_logistics`;
CREATE TABLE `product_logistics`
(
    `id`                bigint(20)    NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`         int(11)       NOT NULL DEFAULT 0 COMMENT '租户ID（0=平台级共享，本模块所有数据均为0）',
    `product_id`        bigint(20)    NOT NULL DEFAULT 0 COMMENT '商品ID，关联product.id；一个商品最多一行',
    `net_weight_kg`     decimal(10,3) NULL COMMENT '净重（kg）；未维护为NULL，不是0',
    `gross_weight_kg`   decimal(10,3) NULL COMMENT '毛重（kg，含包装）；填写时不得小于净重',
    `length_mm`         decimal(10,1) NULL COMMENT '单品长（mm）',
    `width_mm`          decimal(10,1) NULL COMMENT '单品宽（mm）',
    `height_mm`         decimal(10,1) NULL COMMENT '单品高（mm）',
    `package_type`      varchar(32)   NOT NULL DEFAULT '' COMMENT '包装类型，如 盒装/箱装/托盘',
    `package_length_mm` decimal(10,1) NULL COMMENT '包装长（mm）',
    `package_width_mm`  decimal(10,1) NULL COMMENT '包装宽（mm）',
    `package_height_mm` decimal(10,1) NULL COMMENT '包装高（mm）',
    `package_quantity`  int(11)       NULL COMMENT '每个包装内的件数',
    `is_dangerous`      tinyint(2)    NOT NULL DEFAULT 0 COMMENT '是否危险品或含电池等限运品（0-否、1-是）',
    `shipping_note`     varchar(255)  NOT NULL DEFAULT '' COMMENT '运输备注，如 需防潮、含锂电池',
    `deleted_at`        datetime      NULL COMMENT '软删除时间，NULL表示未删除',
    `create_time`       datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`         varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`       datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`         varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_product` (`tenant_id`, `product_id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品物流信息表（一对一）';

DROP TABLE IF EXISTS `product_customs`;
CREATE TABLE `product_customs`
(
    `id`                   bigint(20)    NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`            int(11)       NOT NULL DEFAULT 0 COMMENT '租户ID（0=平台级共享，本模块所有数据均为0）',
    `product_id`           bigint(20)    NOT NULL DEFAULT 0 COMMENT '商品ID，关联product.id；一个商品最多一行',
    `hs_code`              varchar(10)   NOT NULL DEFAULT '' COMMENT 'HS编码，仅数字（去掉点和空格后6~10位）；一个商品一个，空表示未维护',
    `customs_name_cn`      varchar(128)  NOT NULL DEFAULT '' COMMENT '申报品名（中文）',
    `customs_name_en`      varchar(128)  NOT NULL DEFAULT '' COMMENT '申报品名（英文）',
    `origin_country`       char(2)       NOT NULL DEFAULT '' COMMENT '默认原产国，ISO 3166-1 alpha-2大写（如DE/CN）；同一型号不同批次可能不同，实际以货物单据为准',
    `declaration_elements` varchar(500)  NOT NULL DEFAULT '' COMMENT '申报要素',
    `supervision_conditions` varchar(32) NOT NULL DEFAULT '' COMMENT '监管条件代码，如A/B；无则为空',
    `export_rebate_rate`   decimal(5,2)  NULL COMMENT '出口退税率（%，0~100）；政策会调整，以最近一次维护为准；未维护为NULL',
    `deleted_at`           datetime      NULL COMMENT '软删除时间，NULL表示未删除',
    `create_time`          datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`            varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`          datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`            varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_product` (`tenant_id`, `product_id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_hs_code` (`hs_code`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品海关信息表（一对一）';

DROP TABLE IF EXISTS `product_reference_price`;
CREATE TABLE `product_reference_price`
(
    `id`             bigint(20)     NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`      int(11)        NOT NULL DEFAULT 0 COMMENT '租户ID（0=平台级共享，本模块所有数据均为0）',
    `product_id`     bigint(20)     NOT NULL DEFAULT 0 COMMENT '商品ID，关联product.id；一个商品最多一行',
    `price_original` decimal(18, 2) NOT NULL COMMENT '参考价（原币），必须大于0；这是平台层面的参考价，不是任何租户的报价或售价',
    `currency_code`  char(3)        NOT NULL COMMENT '币种（ISO 4217，如USD/CNY）；金额必须带币种',
    `exchange_rate`  decimal(18, 6) NULL COMMENT '汇率（原币→本位币CNY）；币种为CNY时为1；非CNY且汇率未维护时为NULL',
    `price_cny`      decimal(18, 2) NULL COMMENT '参考价（本位币），由原币与汇率计算，HALF_UP保留2位；汇率未维护时为NULL（展示为"未计算"，不是0元）',
    `price_source`   varchar(128)   NOT NULL DEFAULT '' COMMENT '价格来源，如 厂商官网目录价、eBay参考价',
    `price_date`     date           NULL COMMENT '取价日期；未知为NULL',
    `deleted_at`     datetime       NULL COMMENT '软删除时间，NULL表示未删除',
    `create_time`    datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`      varchar(32)    NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`    datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`      varchar(32)    NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_product` (`tenant_id`, `product_id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品平台参考价表（一对一）';
```

### 12. 技术资料、应用场景、FAQ 并入本 change

**背景**：原计划作为 M2 另起 `add-product-master-content`（v1.2.1）。用户于 2026-09-19 要求一并设计，因此内容三张表并入后共九张，同进 `schema_v1.2.sql`（2026-09-19 又增加图片视频、物流、海关、参考价，共十三张，见决策 13），随 v1.2.0 交付；实现顺序上内容类任务排在商品主体之后（tasks 5.12 起），商品主体完成即可支撑询盘关联。

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

### 13. 图片与视频、物流、海关、参考价并入本 change

**背景**：用户于 2026-09-19 要求商品主数据还包括图片、视频、物流、海关、价格信息。价格与决策 3 冲突（共享的商品表不能放各租户自己的价格），经确认选择**平台共享参考价**；图片视频选择**平台共享且支持上传**；HS 编码**一个商品一个**。

**决策**：

1. **图片与视频**：`product_media`（一对多）。图片限 `jpg/jpeg/png/webp` 且 ≤5MB，视频限 `mp4/webm` 且 ≤100MB。可以上传，也可以登记外链（`storage_type` 区分）。`product.main_image_url` **删除**，主图由 `is_main=1` 表示，避免两处各存一份；同一商品最多一张主图，设主图时锁商品行、取消原主图、置新主图，同一事务完成。视频不能设为主图（`MEDIA_NOT_IMAGE`）。
2. **上传的安全要求**（`coding.md` 要求服务端校验）：同时校验扩展名和文件头，两者不一致或不在白名单内一律拒绝；**不允许 SVG**（可内嵌脚本）；存盘文件名用随机串，不使用客户端文件名；上传接口加限流；大文件以流式写盘，不整体读入内存。外链与视频封面沿用 `file_url` 的地址规则：`http://`、`https://` 或单个 `/` 开头（`MEDIA_URL_INVALID`）。
3. **存储抽象**：商品模块新建自己的存储接口（`ProductMediaStorageService`），沿用询盘附件的做法——返回可访问的 URL，当前落本地磁盘（`zhul.upload.dir` 下的 `product` 子目录），后续迁 OSS 时只替换实现类。**不复用询盘模块的接口**，因为依赖方向应是各业务模块 → 公共能力，而不是商品模块 → 询盘模块。
4. **物流**：`product_logistics`（一对一）。重量固定 kg（3 位小数）、尺寸固定 mm（1 位小数），列名带单位，避免单位歧义；未维护为 NULL，不是 0。毛重填写时不得小于净重。`is_dangerous` 标记危险品或含电池等限运品，`shipping_note` 写具体说明。
5. **海关**：`product_customs`（一对一）。`hs_code` 一个商品一个，去掉点和空格后须为 6–10 位数字，只存数字；原产国为 ISO 3166-1 alpha-2 大写码；出口退税率为 0–100 的两位小数。**原产国是默认值**：同一型号不同批次可能产自不同国家（独立站规格里就有 `Germany / China`），实际以货物单据为准，这一点在界面上要写明。
6. **参考价**：`product_reference_price`（一对一）。它是**平台层面的参考价**（厂商目录价、市场参考价），不是任何租户的报价或售价，界面上要写明。沿用询盘报价的做法：原币 + 币种 + 汇率 + 本位币；币种必填才能存金额；金额必须大于 0；汇率未维护时本位币为 NULL，界面显示"未计算"，不是 0。金额与本位币保留 2 位、HALF_UP，汇率 6 位；币种为 CNY 时汇率为 1。本位币只在保存时按当时汇率计算，不随汇率变动自动重算。汇率由请求传入（与询盘报价 `inquiry_order_item_quote` 一致：现有系统没有汇率表，服务端只用传入的汇率计算本位币）；未传汇率且币种不是 CNY 时本位币为空。
7. **一对一表的清除与恢复**：唯一键 `(tenant_id, product_id)` 含已软删除行。物流、海关没有 DELETE，清空字段即可；参考价的 `DELETE` 是软删除，之后再 `PUT` 时复活原行并覆盖内容。
8. **权限与读取**：沿用 `product:product:edit` + `PlatformScopeGuard`，不新增权限码；读取对所有租户开放。随商品一起被删除和恢复。

**理由**：图片、视频、重量、HS 编码、原产国都是关于零件本身的事实，适合共享；参考价是市场信息，不属于任何一个租户的商业决策。租户各自的采购价和售价仍留给「租户商品扩展」。

**备选方案**：(a) 价格做成租户级表：更接近"报价"，但要改成"写入按租户"的权限模型，用户未选。(b) 保留 `main_image_url` 再加图库表：两处各存一份主图，容易不一致。(c) 复用询盘模块的附件存储：依赖方向反了。(d) 按目的国存多条 HS 编码：录入量大，目前没有数据来源，用户选了一个商品一个。均已否决。

### 14. 新建向导、档案完整度与缺项筛选

**背景**：用户于 2026-09-19 认为原型没达到消费级，要求站在新人角度简化流程：新建商品改成分步向导，详情页改成单页卡片，并让人一眼看出还缺什么。这引入了几项后端和前端的约定。

**决策**：

1. **创建时机在向导第 2 步**：第 2 步点「创建并继续」时调用 `POST /products`（品牌、型号、品类、系列、生命周期）；第 3 步的图片走 `POST /products/{id}/media/upload`，第 4 步的名称、规格摘要、简介走 `PUT /products/{id}`。图片上传接口挂在已存在的商品下，所以不做"临时上传区"。
2. **草稿只存前端本地**：第 1、2 步的输入保存在浏览器本地存储（键带账号标识，7 天过期），商品创建后清除；**服务端不新增草稿表**。代价：不跨设备。
3. **档案完整度是计算值，不落库**：共 10 个模块（口径见 PRD 2.14）。`GET /products` 和 `GET /products/{id}` 对平台账号返回 `completeness`（已完成数、总数、各模块是否完成），租户账号不返回。列表按当前页的商品 ID **批量**查各子表，不逐个商品查询。
4. **缺项筛选**：`GET /products?missing=media|logistics|customs|price` 用 `NOT EXISTS` 子查询实现，仅平台账号；租户传入被忽略。`completeness-summary` 返回各缺项的商品数。当前数据量只有 87 个商品，统计不做缓存，商品数超过 1 万时再评估。
5. **相近型号**：向导第 1 步取归一化型号的前 6 位，调用带 `brandId` 的 `search`，在该品牌下取最多 3 条。
6. **卡片独立保存**：档案页每张卡片各自调用对应的模块接口，没有"整页保存"，不需要新增后端接口。
7. **界面风格**：采用 V3 方案 D（信任蓝 + 中性灰，渐变限用于 Logo、主按钮、卡片顶线、关键数字、进度条、向导背景），**深色为默认主题、浅色并存**，品牌主色仍为蓝色系（由 `#1677FF` 调整为 `#2563EB`）；前端用 Ant Design 的深色 / 浅色算法加一套令牌切换（令牌见 `ui-design-patterns.md`）。全站外壳（侧栏、顶栏）需要统一升级，**另立 change**；本 change 的前端先按新风格实现页面内容，外壳沿用现有。

**理由**：完整度是纯派生信息，落库会带来"子表变了但没同步"的一致性问题；向导第 2 步创建能避免临时上传区和孤儿文件清理；草稿本地存储最简单，且符合"第 1、2 步只有几个字段"的实际体量。

**备选方案**：(a) 最后一步一次性提交：需要临时上传区、定期清理未绑定文件、创建时再绑定，复杂度高，已否决。(b) 服务端草稿表：能跨设备，但要新增表、状态和清理任务，本期不值得。(c) 完整度落库并在每次子表变更时刷新：一致性风险，已否决。

## Risks / Trade-offs

- **[风险] 平台账号 `tenantId=0` 的前提未在代码中独立核实**（用户已确认，但仓库无种子账号可对照）→ 实现时用集成测试固化该约定；`PlatformScopeGuard` 是唯一判定点，识别规则变化只改这一处。
- **[风险] 独立站不能完全切换到 ERP 数据源**（决策 3）→ 保持 `LocalProductRepository`；同步更新 wiki 决策文档里 `ErpProductRepository` 的字段映射；租户商品扩展另起 change。
- **[风险] 归一化碰撞**（`A-1B` 与 `A1-B`）→ 以"已存在"阻止并由人工判断；预干跑 87 条无碰撞。
- **[风险] 平台共享意味着一处改动影响所有租户** → 写入仅限平台账号；停用、删除有二次确认；被引用的商品不可删除、不可改型号。
- **[风险] 迁移的 FAQ、应用场景来源无法判断**（`js/data.js` 无来源标记，wiki 日志记录为按品类分批撰写）→ FAQ 全部 `source=3` 待审核（Q7）、应用场景 `verified=0`；租户在人工审核前读不到待审核 FAQ。
- **[风险] FAQ 夹带卖家承诺**（决策 12）→ 干跑按关键词标出并**不导入**；关键词会有误伤和漏网，所以干跑输出完整清单供人工复核，验收要求导入结果中不含 `Fouwell`。
- **[风险] 唯一一份技术资料的文件不在仓库里**（`/datasheets/6ES7212-1AE40-0XB0.pdf` 是独立站相对路径，`fouwell-website/` 下没有 `datasheets/` 目录）→ 原样迁入并 `verified=0`，干跑报告；文件由独立站部署提供，本 change 不做上传。
- **[权衡] 内容表没有唯一键** → 应用层查重，并发下可能重复，写入仅限平台管理员，可接受（决策 12）。
- **[风险] 上传文件的安全**（伪装扩展名、SVG 内嵌脚本、超大文件占满磁盘、路径穿越）→ 扩展名加文件头双重校验、禁 SVG、随机文件名、限流、流式写盘、上传目录不解析执行脚本（决策 13）。
- **[风险] 本地磁盘存储不适合多实例部署**（文件只在一台机器上，无备份）→ 通过存储接口保持可迁 OSS；上线多实例前必须先迁移，列入 Open Questions（Q13）。
- **[风险] 参考价被误当作报价** → 表名、字段注释和界面文案都写明"平台参考价，不是报价或售价"；租户各自的价格仍不进共享表。
- **[风险] 汇率变化后本位币金额过时** → 本位币只在保存时计算，界面同时显示所用汇率；不自动重算，避免改动已展示的数字。
- **[风险] 完整度的批量查询在商品量大时变慢** → 每页只查当前页商品；`missing` 筛选用 `NOT EXISTS`；商品数超过 1 万时评估汇总表或缓存（决策 14）。
- **[权衡] 草稿只存浏览器本地** → 换设备或清缓存会丢失第 1、2 步的输入，但这两步只有几个字段，丢失代价很小。
- **[风险] 商品页新风格与现有外壳不一致** → 全站外壳升级另立 change，升级前商品页沿用旧外壳，视觉上有差异，需要在上线前处理（Q14）。
- **[权衡] 原产国只存默认值** → 不按批次记录；界面注明以货物单据为准，批次级信息留给采购或入库模块。
- **[权衡] 唯一键含已软删除行** → 已删除的型号只能恢复，不能重建；换来标识稳定。
- **[权衡] 产品名称搜索为 `LIKE '%kw%'`，不走索引** → 目录规模下可接受，上量后再评估全文索引。
- **[权衡] 一次交付十三张表** → 范围比只做六张表大；靠 tasks 顺序（内容类任务排在商品主体之后）保持可分段合并，商品主体完成即可支撑询盘关联。

## Migration Plan

全部为新增表和新增模块，不修改任何已有表，风险面小。

1. 执行 `schema_v1.2.sql`（十三张表）；回滚仅需 `DROP` 这十三张表
2. `resource` 表新增菜单与按钮资源（`type=3`）
3. 迁移脚本（Node，读取 `fouwell-website/js/data.js`），先**干跑**，再导入，生成幂等 SQL（`INSERT ... ON DUPLICATE KEY UPDATE`）。所有迁移行 `create_by='migration'`，回滚：`UPDATE ... SET deleted_at=NOW() WHERE create_by='migration'`。内容类三张表用 `INSERT ... SELECT ... WHERE NOT EXISTS`（同商品下同文件地址 / 标题 / 问题）保持幂等。干跑必须报告（不得静默合并或静默丢弃）：同品牌下归一化型号碰撞、品牌名近似重复、空型号、缺品类、**FAQ 中疑似卖家承诺的条目（完整清单与命中的关键词）**、技术资料文件是否存在、**参考价迁入与排除的条数**、规格中的重量 / 尺寸 / 原产国自由文本条数（不自动解析，只报数）

| `js/data.js` | ERP | 规则 |
|--------------|-----|------|
| `brand` | `product_brand.brand_name` | 去重后 40 个；国家 / 主题色 / `is_genuine` 取自独立站品牌数据与 `NON_GENUINE_BRANDS`（`General`、`PWERUN` 为非原厂）；独立站品牌表里没有的 21 个品牌国家和主题色留空。**Logo 不迁移**：独立站的 Logo 是站内相对路径（`/assets/brands/real/xxx.svg`），在 ERP 域名下打不开，界面回退为字母标，之后由平台账号上传 |
| `cat` | `product_category.category_code` | 6 个 key 原样使用（`controllers/drives/servo/sensors/hmi/spares`）；名称取自独立站品类名称表 |
| `series` | `product_series` | 按（品牌, 系列）去重创建，预期 80 个 |
| `model` | `mpn_raw` / `mpn_display` | 原样；`mpn_normalized` 由归一化函数生成 |
| `spec` | `spec_summary` | |
| `status` | `lifecycle_status` | `instock`→1、`legacy`→3、`discont`→4；带 `no_known_replacement` 的 1 条→5；`lifecycle_source` 写"迁移自独立站 status=xxx，未逐条核实" |
| `specs`（84 条，`[标签, 值]`） | `product_specification` | `spec_label`=标签，`spec_value`=值，`spec_unit` 留空（值里自带单位，无法可靠拆分），`spec_key`=标签转小写蛇形（同商品内重名加 `_2`），`verified=0`，`source`=独立站迁移 |
| `photo`（52 张）、`linkedin` | 不迁移 | 福唯自己的现货实拍图和营销图，不是零件通用图（决策 13）；迁移后主图为空，由平台账号上传官方图 |
| `compatibility`（12 个商品共 22 条，`{from, type?, note}`） | `product_relationship` | **已检视（2026-09-20）**。`from` 是"较早的型号"（当前商品替代它 / 是它的后续），`type` 有 `direct`→1、`successor`→2、`functional`→3、`compatible`→4（共 10 条带 type），没有 type 的默认类型 5、置信度 3。**方向约定**：关系挂在商品 A 下，读作"关联型号 X 是 A 的{官方替代 / 后续型号}"，所以 `direct` / `successor` 要挂在 `from` 对应的商品下、关联到当前商品；`from` 不在目录里时无法建立（干跑报告）。对称类型（`functional` / `compatible` / 默认 5）挂在当前商品下，两端都在目录内时补反向关系。**`from` 等于自己**（如 FR-A740-7.5K 自己的条目，替代型号只写在说明里）跳过，由对端条目覆盖；**`from` 是一段描述而不是型号**（如 "Older non-EtherCAT Accurax G5"）无法自动关联，报告后由人工处理。说明里含卖家措辞的去掉说明。实测：导入 8 条，跳过 3 条，人工 12 条 |
| `sell_price` + `sell_price_currency` + `price_source`（`ebay_ref` 53 条、`web_ref` 3 条，共 56 条） | `product_reference_price` | `price_original`=`sell_price`，`currency_code`=`USD`，`exchange_rate` / `price_cny` 留空（汇率未维护），`price_source`=`eBay 参考价（独立站迁移）` 或 `网络参考价（独立站迁移）`，`price_date` 留空 |
| `sell_price`（`procurement_quote_min` 14 条）、`condition_note*` | 不迁移 | 采购报价是福唯自己的成本，属租户级数据（决策 3） |
| `specs` 中的 `Net weight`、`Dimensions`、`Origin` / `Country of origin` | 不自动迁移 | 自由文本且混有多值（如 `Germany / China`），干跑只报数（重量 1、尺寸 1、原产国 26），由人工录入物流与海关 |
| `product_logistics`、`product_customs` | 迁移后为空 | 没有可靠来源，HS 编码一个都没有 |
| `datasheet`（仅 1 个商品，字符串路径） | `product_document` | `document_type=1`、`title`=`Datasheet`、`file_url`=原路径、`language`=`en`、`verified=0`、`source`=独立站迁移；文件不在仓库，干跑报告 |
| `applications`（84 个商品共 117 条，`{icon, title, desc}`） | `product_application` | `title`、`description`=`desc`、`icon`=`icon`（emoji 原样，29 种）、`sort_order`=数组顺序、`verified=0` |
| `faq`（84 个商品共 222 条，`{q, a}`） | `product_faq` | `question`=`q`、`answer`=`a`、`source=3`（Q7）、`sort_order`=数组顺序；**命中卖家承诺关键词的条目不导入**（决策 12）。关键词范围：品牌名 `Fouwell`、邮箱与电话、质保 `warranty`、库存与发货 `in stock` / `ship` / `lead time` / `MOQ`、价格 `price` / `quote`、第一人称 `we` / `our`；以干跑清单人工复核为准，误伤的条目由人工加入放行清单（`scripts/product-migration/faq-allowlist.json`）后再导入。**实测（2026-09-20）**：222 条中命中 140 条被排除、放行 1 条（答案里的 `price-per-I/O` 是产品对比，非卖家承诺），导入 82 条（69 个商品）；命中的多数是"库存 / 发货 / 质保"这类卖家问答，或答案里夹带 "we / our / tell us" 的措辞 |

验收：商品 87 / 品牌 40 / 品类 6 / 系列 80，无唯一键冲突；参考价 56 条（全部 USD、本位币为空），排除 14 条采购报价；图片视频、物流、海关 0 行；技术资料 1 条；应用场景 117 条（84 个商品）；FAQ = 222 − 干跑排除数 140 = 82，全部 `source=3`，且导入结果中没有任何一条含 `Fouwell`；抽查 5 条商品，规格与独立站页面一致。

## Open Questions

- ~~**Q2（上线门槛）平台账号 JWT 的 `tenantId` 是否为 0？**~~ **已确认（2026-09-19）：是 0。** 见决策 2 的前提。
- Q1 的前半（是否同意把价格、库存等移出商品主表）**已确认同意**（决策 3）。**剩余**：「租户商品扩展」（价格、库存、成色、MOQ、交期、站点发布配置）放在 v1.2.1、v2.0 报价中心还是 v2.2 仓储域？决定独立站何时能切换到 ERP 数据源，不影响本 change。
- Q3：租户用户能否提交新型号？（默认只有平台账号能建商品；租户用户在询盘里选已有商品或留空。）
- Q4：品类是否需要中文名？（默认只有英文名，与独立站 URL 编码一致。）
- Q5：商品是否需要"前缀+主键"业务编码？（默认不需要，MPN 本身即业务标识。）
- Q6：计量单位是否单独做字典？（默认本期不做。）
- Q7：迁移的 FAQ（**84 个商品共 222 条**，不是 84 条）是否视为已人工审核？（默认保守置为 `source=3` 待审核；卖家承诺类条目已决定不导入，见决策 12。）
- Q11：被排除的卖家类 FAQ 与站点内容放在哪里？（默认只保留在干跑报告与独立站本地 `js/data.js`，不改独立站；「租户商品扩展」落地时再迁移。）
- ~~Q8：HS 编码、原产国、重量体积是否预留？~~ **已纳入（2026-09-19）**：见决策 13，HS 编码一个商品一个。
- ~~Q12：参考价的汇率取自哪里？~~ **已解决（2026-09-19）**：现有询盘报价由请求传入汇率，系统里没有汇率表；参考价沿用同样做法。
- Q14：全站外壳（侧栏、顶栏）与主题令牌升级由谁负责、何时做？（用户已确认采用深色默认、浅色并存，品牌主色由 `#1677FF` 调整为 `#2563EB` 并增加蓝 → 靛 → 青渐变；这会影响全站，本 change 不含，需另立 change，如 `upgrade-app-shell`；升级前商品页沿用旧外壳。）
- Q13：图片视频的存储何时迁 OSS？（当前落本地磁盘，多实例部署或需要备份前必须迁移；默认上线前不迁，单实例部署。）
- Q9：独立站构建机能否访问 ERP API？（wiki 遗留问题；若 ERP 在内网，"实时调用"方案不成立。）
- Q10：询盘明细 `category` 是 `varchar(32)` 自由文本，如何映射到 `category_code`？（默认关联商品时以商品的品类为准，历史明细不回填；属 `link-inquiry-to-product`。）
- wiki 记录商品数为 84、实测 87：**已查清**，84 是 2026-09-18 全站深度页升级时的独立站SKU数，同日又发布 3 个 WECON 型号（84→87），与预干跑一致；`compatibility` 子字段结构未检视；`resource` 表种子数据的具体写法未找到样例。
