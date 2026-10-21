## Context

- **营销内容**（`product/content`）按公司保存：
  - `product_content_task` 每家公司每个商品一行，记录各平台的写入时间：`alibaba_at`、`mic_at`、`social_at`，独立站是 `en_at` 和 `ru_at`。
  - `MarketingPlatform.written()` 判断某个平台是否写完。
  - `ContentTaskService.markWritten()` 在每次确认写入时刷新对应时间。
- **资料状态**：存在共享商品库 `product_fact_task`（租户 0），`verified_at` 不早于 `imported_at` 即为「资料已核」。`FactTaskService.verify()` 核对后对所有公司生效。
- **不发布与平台设置**：
  - 不发布存在 `product_marketing_skip`，由 `MarketingSkipService.mark/unmark` 维护。
  - 启用平台由 `PlatformSettingService.enabled(tenantId)` 计算，`save()` 修改。
- **素材库**：`media_item` 按 `asset_key`（品牌 + 型号，小写）索引，有 `for_listing` 标记。`MediaProducts` 负责商品与型号键的对应，`MediaReferenceService` 负责查找参考素材。前端有 `MediaGrid`、`ReferencePanel` 等组件可以复用。

## Goals / Non-Goals

**Goals**
- 按「商品 × 平台」持久化发品任务，记录发布结果。
- 生成与取消由业务事件驱动，并能整体重算（补生成、修复都走同一个方法）。
- 素材待补、内容有更新、待替换素材都是查询时算出来的，不存冗余状态。

**Non-Goals**
- 不调用平台接口自动发布。
- 不支持多店铺。
- 不跟踪平台上的下架状态。
- 不做发品统计。

## Decisions

### 1. 数据表 `listing_task`（业务表）

| 字段 | 说明 |
|---|---|
| `id` | 主键 |
| `tenant_id`, `product_id`, `platform` | 唯一键 `uk_tenant_product_platform` |
| `status` | 1-待发布、2-已发布、3-已取消 |
| `cancel_reason` | 已取消时的原因 |
| `ref_brand`, `ref_model` | 选用的参考素材型号，为空表示用本型号素材 |
| `ref_by`, `ref_time` | 选择参考素材的人和时间 |
| `listing_url` | 平台链接，varchar(500) |
| `platform_item_id` | 平台商品编号，varchar(64) |
| `note` | 备注，varchar(300) |
| `published_by`, `published_at` | 发布人、发布时间 |
| `content_at` | 发布或上次同步时，这个平台营销内容的写入时间（独立站取英文、俄文中较晚的） |
| `synced_by`, `synced_at` | 上次同步的人和时间 |
| `replaced_by`, `replaced_at` | 上次替换素材的人和时间 |
| 审计列 | 创建时间、创建人、更新时间、更新人，以及 `deleted_at` |

**为什么不复用 `product_content_task`**：营销写入是「商品一行」，发品是「商品 × 平台一行」，还要存发布记录。分开存，营销任务那边的逻辑不受影响。

**派生状态（查询时计算）**
- **素材待补**：status=1，本型号没有 `for_listing=1` 的素材，且 `ref_model` 为空。
- **内容有更新**：status=2，当前写入时间晚于 `content_at`。
- **待替换素材**：status=2，`ref_model` 不为空，且本型号已有 `for_listing=1` 的素材。

### 2. 生成与取消：`ListingTaskSync.sync(tenantId, productIds)`

**单一入口，幂等**。对每个「公司 × 商品 × 平台」：

- **满足生成条件**（资料已核、平台启用、没标不发布、平台写完）：
  - 没有任务时，新建「待发布」；
  - 任务是「已取消」时，回到「待发布」，清空取消原因。
- **不满足启用或不发布条件**：「待发布」的任务改为「已取消」，记下原因。
- **其他情况**（例如资料回到待核、内容被删除）：不动。

**调用点**：各处发布 `MarketingChangedEvent`，`ListingTaskSync` 同步监听，在发布方的事务里处理，失败时一起回滚；用事件是为了避免内容模块和发品模块互相依赖。

| 触发 | 范围 |
|---|---|
| `ContentWriter` 写入营销内容后 | 本公司、该商品 |
| `FactTaskService.verify()` | 所有有该商品 `product_content_task` 的公司，逐个公司处理，查询时显式带 tenant_id |
| `MarketingSkipService.mark/unmark` | 本公司、该商品 |
| `PlatformSettingService.save()` | 本公司、所有有营销写入的商品，分批每批 500 个 |

**平台账号保存默认设置时**：没自己设置过的公司要按新默认值重算。这里只更新新写入的公司，其余公司在下次相关事件时重算。每次启动时的补齐（见迁移计划）会让这些公司对齐到新默认值。

**跨租户查询**：`verify` 是共享资料操作，要对多个公司写任务。通过显式标注的跨租户查询拿到公司列表，再对每个公司设置租户上下文后调用 `sync`。

### 3. 素材

**本型号素材**：取商品的品牌名与型号算出 `asset_key`，查 `media_item` 中 `for_listing=1`、未删除的项。

- 预览和下载复用素材库的 `urls` 接口：返回素材引用，前端调用 `mediaApi.urls`。
- 仅内部的素材本来就不能用于发品，所以不需要另外校验。

**参考素材**：`ref_brand` 和 `ref_model` 记住型号，详情返回这个型号的可发客户图片和视频（复用 `MediaLibraryService.detail` 的分组，展开为平铺列表）。

- 选择时用 `MediaReferenceService` 校验：所选型号必须出现在本型号的参考列表里，否则拒绝。
- 待发布任务在本型号有了发品素材时，详情和列表都按本型号显示（派生）。`ref_*` 保留不清，以免本型号素材又被取消勾选后丢失之前的选择。
- 已发布任务的 `ref_*` 在点「已替换」时清空。

### 4. 列表与计数 SQL

`listing_task` 关联 `product`、`product_brand`、`product_category`，共 4 张表，超过 3 张表的 JOIN 限制。因此改成：

1. 先在 `listing_task` 上按状态、平台分页，商品筛选（品牌、品类、关键词）先查出 `product_id` 集合再 `IN`；
2. 商品信息和素材数在 Service 层批量组装。

派生页签不能只靠 `listing_task` 字段判断：

- **内容有更新**：用 `listing_task` 与 `product_content_task` 两表 JOIN 判断（`content_at` < 对应平台写入时间）。
- **素材待补、待替换素材**：用 `NOT EXISTS` / `EXISTS` 子查询 `media_item`，按商品的 `asset_key` 列。为此在 `listing_task` 上冗余 `asset_key` 列，生成时写入。商品改型号的情况极少，`sync` 时一并刷新。

### 5. 复制格式（前端）

- 属性：每行「名称: 值」。
- 卖点：每行一条。
- FAQ：每条两行，「Q: …」「A: …」，条之间空一行。
- 关键词：英文逗号加空格。
- 详情：原文。

复制用 `navigator.clipboard.writeText`；不可用时退回到选中文本并执行 `execCommand('copy')`。

### 6. 接口（`/api/v1/listing-tasks`）

| 方法与路径 | 说明 |
|---|---|
| `POST /page`、`GET /counts` | 列表与各页签数量 |
| `GET /{id}` | 详情：任务、商品、营销内容（复用 `ContentTaskService.productContent` 的平台块）、素材、提醒 |
| `POST /{id}/urls` | 任务素材的预览或下载链接，只能取这个任务显示的素材（只有发品任务权限、没有素材库权限的人也能看素材） |
| `GET /{id}/references` | 这个任务型号的参考型号，选参考素材用 |
| `PUT /{id}/reference` | 设置或取消参考素材，body：`{brand, model}`，为空表示取消 |
| `POST /{id}/publish` | 标记已发布，body：`{listingUrl, platformItemId, note}` |
| `PUT /{id}/record` | 修改发布记录 |
| `POST /{id}/sync`、`POST /{id}/replace` | 已同步、已替换 |

- 查看用 `@perm.canAccessMenu('/operations/listings')`，处理用 `@perm.has('operations:listing:edit')`。
- 写操作锁任务行（`SELECT … FOR UPDATE`），按状态校验。
- 重复点击不重复记录：已同步、已替换在没有提醒时返回「没有需要同步的更新」「没有需要替换的素材」。

### 7. 权限与菜单

- 菜单 100117「发品任务」，路径 `/operations/listings`，挂在运营管理 100101 下，排在营销任务之后、素材库之前（素材库的排序号顺延）。
- 按钮 110213 `operations:listing:edit`。
- 两项加入所有套餐，ROLE_ADMIN 拥有。

## Risks / Trade-offs

- **平台设置变更时重算量大**：商品多的公司一次重算可能有几千个商品。分批处理，每批一条查询加批量写，可以接受；超时再改为异步。
- **派生页签的查询成本**：`EXISTS media_item` 依赖 `media_item(tenant_id, asset_key)` 索引（已有唯一键前缀可用），需要确认执行计划。
- **商品型号改名**：`asset_key` 冗余可能过期，由 `sync` 刷新。在下次相关事件之前，素材可能暂时对不上，影响很小。
- **`content_at` 的精度**：写入时间精确到秒。同一秒内既确认又发布的情况视为没有更新。

## Migration Plan

- **`V1.3.4__listing_task.sql`**：建 `listing_task` 表；加菜单与按钮权限，加入套餐和 ROLE_ADMIN。
- **补生成任务**：平台启用要按「公司设置 → 平台默认 → 四个都启用」解析，用 SQL 重写一遍容易和代码不一致。因此迁移只建表和权限，补生成由应用启动时的 `ListingTaskBackfill` 完成：逐个公司调用同一个 `syncAll`，每个公司一个事务。对齐是幂等的，所以每次启动都跑，不需要「已执行」标记；这样平台账号改了默认设置、或个别事件处理失败时，下次启动也能补上。
- **反向 SQL**：删除权限与套餐里的 ID，删除 `listing_task` 表（发品记录会丢失，回滚前先导出）。

## Open Questions

- 无。生成时机、素材要求、单店铺、内容更新提醒已于 2026-10-17 与用户确认。
