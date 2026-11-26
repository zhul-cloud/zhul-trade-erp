## Context

- llm-wiki 有三张追踪表（`wiki/products/GSC|Bing|Yandex索引提交追踪表.md`），各 214 行，类型包括型号页、品牌 Hub、品类 Hub、核心页面：
  - GSC 表的列：URL、类型、创建时间、上线日期、提交状态、提交日期、收录确认日期、备注；
  - Bing 和 Yandex 表的列：URL、类型、创建时间、收录确认状态、确认日期、备注。
- 追踪表由站点仓库的 `sync-indexing-tracker.js` 按 `sitemap-v2.xml` 增量维护。Google 的提交靠 `gsc-submit` 技能，用浏览器每 4 分钟提交一条。
- `website-publish` 的发布服务在发布完成后推送 IndexNow，并回调发布结果。
- Search Console URL Inspection API（`urlInspection.index.inspect`）：
  - 需要 OAuth，服务账号要在 Search Console 里加为资源用户；
  - 每个资源每天 2000 次、每分钟 600 次；
  - 只能查询，不能请求编入索引。

## Goals / Non-Goals

**Goals:**
- 发布后自动有跟踪记录，人工只做 Google 提交和收录确认两件事；
- 接管 llm-wiki 已有的跟踪进度，不重复提交。

**Non-Goals:**
- 模拟浏览器自动在 Search Console 提交（违反服务条款，而且不稳定）；
- Indexing API（Google 只对招聘和直播页面开放）。

## Decisions

### 1. 新增链接由站点地图前后对比得出

- 发布服务在「拉取仓库」之后、「构建」之后各解析一次站点地图（由 `erp/build.sh` 输出 `erp/urls.json`：链接、语言、页面类型）。两次的差集就是新增链接，随发布结果一起回调。
- **为什么不只用商品链接**：发布新品牌时会顺带生成品牌页、品类页，这些页面也要收录；只有站点自己知道生成了哪些页面。
- 导入时用当时的 `urls.json` 全量生成记录。

### 2. 数据模型

| 表 | 主要字段 |
| --- | --- |
| `website_index_record` | tenant_id、site_id、url（站点 + 链接 + 引擎唯一）、lang、page_type、website_product_id（可空）、engine（google / bing / yandex）、status、online_time、submit_time / submit_by、push_time、push_error、indexed_time / indexed_by、last_check_time、last_check_note、remark、deleted_at |
| `website_index_setting` | site_id、engine、enabled、langs JSON、gsc_property、服务账号密文、last_error |

- 两张表都在 `TenantTables` 里归为私有表（setting 按站点归属，通过 site 校验）。
- 「超期未收录」不单独存，由提交或推送时间计算；每天 08:00 的任务只负责刷新列表计数缓存。考虑到「按超期筛选」的需求，计算字段直接用 SQL 条件 `COALESCE(submit_time, push_time) < now - 14 天 AND status <> 已收录`，不需要定时写入。规格里的 08:00 检查，实现上等价于查询时计算。

### 3. Google 自动查询

- 定时任务每天 09:00 按 `TenantScope.platform` 遍历配置了服务账号的站点，逐个用 `TenantContext.runAs` 执行。
- 服务账号的 JSON 用 `AesUtils` 加密保存，执行时在内存里解密。用 Google 官方 Java 客户端（`google-api-services-searchconsole`）获取令牌并调用。
- 限速：用 Guava `RateLimiter`，每秒 1 次（低于每分钟 600 次的上限）；每天 200 条上限写成常量，以后需要再做成配置。
- 判断已收录：`indexStatusResult.verdict == PASS`。`coverageState` 原文记入 `last_check_note`。
- 遇到 403 或 404 时，写入 `last_error`，跳过这个站点；遇到 429 时，当天停止。

### 4. 追踪表导入

- 后端解析 Markdown 表格：按 `|` 分列，第一行是表头，第二行是分隔线。
- 表头名称去掉空格后匹配；状态对照写死在代码里（与规格一致）；日期接受 `YYYY-MM-DD` 和 `YYYY-MM-DD HH:mm`。
- 预览结果放在 Redis（`zhul:erp:index:import:{tenantId}:{token}`，有效期 300 秒），确认时按 token 执行，避免重复上传。

### 5. 重新推送

- 后端直接调用 IndexNow 接口（`https://api.indexnow.org/indexnow`），用站点的 IndexNow 密钥，一次最多 100 个链接。
- 推送很轻，不经过发布服务。

## Risks / Trade-offs

- [站点地图里的链接与发品任务的上线链接写法不一致（结尾斜杠、大小写）] → 比较前统一规范化：小写主机名、保留路径结尾的斜杠、去掉查询参数。
- [Search Console 服务账号配置门槛高] → 自动查询是可选的，没有配置时全部人工确认；收录设置里给出配置步骤说明。
- [追踪表在 Obsidian 里被重新排版（列宽空格）] → 解析时去掉单元格两端空格，按列名而不是列位置识别。
- [人工标「已提交」与实际提交不一致] → 允许撤销；自动查询会兜底更正为已收录。

## Migration Plan

1. 先上线 `website-publish`（包括 `erp/urls.json` 的输出）。
2. 上线本变更，执行数据库迁移（两张表、菜单、权限）。
3. 对 fouwell.com：如果导入已经在本变更之前完成，在站点管理里点「按站点地图补生成收录记录」（导入动作的一部分，可以单独执行）；然后上传三张追踪表。
4. 停用 llm-wiki 的 `sync-indexing-tracker.js` 和 `gsc-submit` 技能对 fouwell.com 的使用，并在 zhul-skills 里注明。
5. **回滚**：本变更只新增表和菜单，下线时隐藏菜单即可，不影响发布。
