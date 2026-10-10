## Context

现有回价链路（`MyTaskServiceImpl.saveQuotes` / 导入确认 → `writeQuotes` → `afterSubmit` → `SourcingProgress`）只有两种回价状态：草稿（1）、已提交（2）。型号回价状态、采购成本价、任务 / 客户询盘回价进度、历史询价、解析确认的历史价复用，全部只读「已提交」的记录。兼职采购通过内置角色 `ROLE_PTBUYER` 识别（`InquiryLookups.isPartTime`）。推荐报价存在 `sourcing_quote.recommended`，没标时 `recommendedDrafts` 按全新原装最低价自动推荐。

## Goals / Non-Goals

**Goals:**
- 兼职的回价在审核通过前对业务员完全不可见，且不影响任何价格计算。
- 审核以「型号 × 兼职采购」为单位，可以分批、部分审核。
- 尽量不动现有「只读已提交」的下游逻辑。

**Non-Goals:**
- 不做站内消息 / 推送提醒（待审核数量在页签上显示即可）。
- 不给正式采购加审核；不支持审核人修改价格内容。
- 不改询价包 Excel 模板（本来就没有推荐列）。

## Decisions

### 1. 用回价状态表达待审核，而不是新建审核单表
`sourcing_quote.status` 增加 `3-待审核`、`4-已作废`。下游只认 `2-已提交`，因此待审核、已作废天然被排除在回价进度、成本价、历史询价和历史价复用之外，`SourcingProgress`、`PriceHistoryServiceImpl`、`CustomerInquiryServiceImpl` 无需改查询条件，只需逐处核对。
- 备选：新建 `sourcing_quote_review` 表记录审核单。审核粒度天然就是「某人对某型号的一批记录」，状态字段足够表达，单独建表只会多一次关联，放弃。

新增字段（迁移 V1.2.25）：
- `review_note varchar(200) NOT NULL DEFAULT ''`：退回原因或作废原因。
- `reviewed_by bigint NULL`、`reviewed_at datetime NULL`：审核人和审核时间。

### 2. 是否需要审核按「回价人」判断
`writeQuotes` 里按 `quotedBy` 是否为兼职采购决定提交后的状态：兼职 → 3，其他 → 2。代兼职导入时 `quotedBy` 是兼职本人，所以同样进入待审核。兼职的推荐一律写 0，跳过 `recommendedDrafts` 的自动推荐，推荐留给审核人。

### 3. 修改已通过的回价：旧版本保留到新版本通过
兼职重新提交时，只把本人在这些型号上的草稿和「待审核」记录软删除，「已提交」（上次审核通过）的记录保持有效；审核通过时才把该兼职在这些型号上原来的「已提交」记录软删除，并沿用现有的「修改回价」操作日志格式留痕。这样业务员在新版本审核期间仍看到上一次通过的价格，退回时旧版本继续有效。

### 4. 审核操作
接口挂在分配工作台控制器下，权限 `@perm.has('inquiry:quote:review')`：
- `GET /api/v1/inquiry/sourcing-board/reviews`：待审核任务分页列表。
- `GET .../reviews/{taskId}`：按型号、按兼职分组列出待审核记录，附历史询价同型号最低价。
- `POST .../reviews/{taskId}/approve`：`[{itemId, quotedBy, recommendedQuoteId, voidQuoteIds, voidReason}]`。同一事务内：校验记录仍为待审核（防并发重复审核，不是时提示刷新）→ 作废 → 该兼职此型号原「已提交」记录软删除 → 待审核转为已提交、写推荐、`quoted_at` 保持兼职提交时间 → `progress.refreshItems/refreshTask/refreshInquiry` → 写操作日志。
- `POST .../reviews/{taskId}/reject`：`{items:[{itemId, quotedBy}], reason}`，待审核记录转回草稿并写 `review_note`。
- 有价记录 ≥1 且未选推荐时报错；只有一条有价记录时前端默认选中它。全部记录都被作废时拒绝通过，提示改用退回。

事务边界：审核通过与提交回价相同，先 `progress.lock(customerInquiryId)` 锁客户询盘，再改回价和重算进度，保证与并发的提交、改派、取消互斥。客户询盘到「已报价」或已取消后不能再审核（与回价只读规则一致）。

### 5. 工作台页签归属
「待审核」不是任务状态，而是查询条件：任务（未取消）下存在 status=3 的回价。「询价中」列表排除这些任务；「已回价」列表不排除（多人比价时正式采购可能已回齐，兼职那份还在审核，此时任务同时出现在「已回价」和「待审核」，前者用于定成本价，后者用于审核）。页签数量用同样的条件统计。

### 6. 兼职侧展示
「我的询价任务」详情接口对兼职返回每条记录的状态与 `review_note`；前端兼职视图不渲染推荐列与推荐标签，型号上显示「待审核 / 已通过 / 被退回：原因」。兼职工作台的「本月回价型号数」按兼职提交时间统计，包含待审核（反映兼职工作量，与是否通过无关）。

## Risks / Trade-offs

- [负责人不及时审核，业务员等不到价格] → 页签显示待审核数与已等待时长，排序把紧急和高等级询盘放前面；后续如需要再接入超时提醒。
- [多人比价时正式采购的价格先可见，兼职的更便宜价格稍后才进入成本价] → 属预期行为；成本价规则不变，审核通过后自动重算。
- [`status` 新增取值后，遗漏某处按 `status != 1` 之类判断] → 实施时逐处检索 `QUOTE_SUBMITTED` / `QUOTE_DRAFT` 的使用点并补测试。

## Migration Plan

1. V1.2.25：`sourcing_quote` 加 3 个字段、更新 `status` 注释；新增按钮资源「审核兼职回价」（`inquiry:quote:review`，挂在分配工作台菜单下），只授给内置角色「租户管理员」。
2. 存量数据不迁移：已提交的兼职回价视为已审核通过。
3. 回滚：新代码下线后，status=3/4 的记录对旧代码不可见（旧代码只认 1/2），不会污染价格；如需恢复可按需把 3 改回 2。

## Open Questions

无（审核不通过退回、不能改价格、分批审核、单独权限四点已与需求方确认）。
