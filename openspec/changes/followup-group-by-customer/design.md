## Context

- 跟进对象 `followup_item` 一张单据一行（询盘、报价单、PI、订单、售后），阶段、下一步、待办都挂在单据上；`customer_followup` 跟进记录用 `followup_item_id` 挂一张单据。
- `overview()` 板块卡片的 `customers` 按客户去重，分组、阶段、逾期、今日待办都按单据计数，这是「数字对不上」「跟进后不变」的直接原因。
- `record()` 只更新一张单据；页面右上角不选单据时挂到「最紧急的一张」。下一步选「今天」时单据仍满足 `next_action_date <= today`，留在今日待办。
- 团队视图「按时跟进」等统计用 `customer_followup.followup_item_id + due_date` 关联跟进对象（`FollowupTeamMapper`）。

## Goals / Non-Goals

**Goals:**
- 阶段、提醒、单据级规则全部保留在单据上，只在查询和记录两处按客户汇总。
- 一次跟进可以覆盖同一客户、同一板块的多张单据；计数与列表口径一致。

**Non-Goals:**
- 不改跟进对象的生成、同步和阶段推导规则；不改主动开发、客户经营的列表。
- 不做跨客户合并跟进记录；批量跟进按客户逐条写记录。
- 团队视图「卡在哪一步」「需要主管看一眼」仍按单据。

## Decisions

1. **客户所处阶段在查询时计算，不落库。** 对每个（板块，客户）取未关闭、未培育单据中阶段顺序最大的阶段码，阶段顺序用现有各板块的分组常量（S1–S7、F1–F6、A1–A6）展开成序号。overview 先按（客户，板块）汇总，再按所处阶段计数；逾期按客户「最早下一步日期 < 今天」。备选：在 `followup_item` 上冗余「客户阶段」字段——每次单据变化都要回写同客户的其他行，同步点太多，放弃。
2. **按客户分组的列表新增接口 `GET /followups/groups`。** 参数与 `/items` 相同；先在 SQL 里按客户聚合（`GROUP BY customer_id`，取 `MAX(stage_rank)`、`MIN(next_action_date)`、`MAX(amount_cny)`）并按客户价值分页，再按页内客户一次查出全部单据组装。阶段 / 分组筛选作用在客户所处阶段上（`HAVING`）。旧 `/items` 保留给单据行内部使用和兼容。
3. **今日待办按客户汇总。** `today()` 先取到期单据，再按（客户，板块）归并；一个客户多个板块合成一张卡片。「今天已跟进」= 今天到期、且 `last_followup_at` 是今天的单据所属客户；这些单据不计入待办数。
4. **一次跟进覆盖多张单据：一条记录 + 关联表。** 新增 `customer_followup_item`（`followup_id`、`followup_item_id`、记录时的单号、阶段和到期日），记录涉及的每张单据；`customer_followup.followup_item_id` 仍写第一张，原有读取不受影响。团队统计里按跟进对象关联记录的只有客户经营（一个客户一张单据）和评价数（按记录计），口径不受影响，继续读原表；历史记录只涉及一张单据，不回填。备选：每张单据写一条记录——40 张询盘会让客户时间线出现 40 条重复记录，放弃。
5. **`RecordFollowupRequest` 增加 `itemIds`。** 右上角按钮和客户详情选客户后，`customerSummary` 返回最紧急板块的全部单据（`currentItems`）用于默认全选。有 `itemIds` 时：全部校验属于该客户、同一板块、未关闭；阶段问题字段只在所选单据阶段全部相同时接受，否则报 `FOLLOWUP_STAGE_MISMATCH`；必答阶段（A1、A2、A5、A6）阶段不同直接拒绝。只有 `itemId` 时按单张处理，行为与现在相同。商机推进按 `opportunity_id` 去重，同一个商机只推进一次。
6. **批量接口逐客户独立事务。** `POST /followups/batch-record`、`POST /followups/batch-snooze`，每次最多 50 个客户；`FollowupBatchService` 逐个客户调用 `FollowupService` 的记录 / 延后方法（跨 Bean 调用，各自一个事务），一个失败不影响其他，返回成功数和失败明细。今日待办里一个客户可能在几个板块都有到期单据，批量跟进按板块各写一条记录（一次跟进只能覆盖同一板块），主动开发、客户经营和必答阶段的那个板块跳过并说明原因。批量跟进的幂等键为「请求幂等键-客户 ID」，分板块时再加「-板块」。今日待办客户卡片的「延后」也走批量延后，只传一个客户。
7. **前端展开状态。** 每个客户行的展开状态存在页面内；「全部展开 / 全部收起」存 localStorage（`zhul:followup:expand-all`），读写都包 try/catch。

## Risks / Trade-offs

- [按客户聚合的 SQL 变重] → 聚合只在当前用户可见范围内进行，`followup_item` 已有 `owner_id`、`board`、`closed_at` 索引；分页只取 20 个客户；上线前用开发库造 1 个客户 × 200 张单据和 2,000 个客户的数据测一次耗时。
- [多张单据阶段不同，阶段问题被隐藏] → 弹窗明确提示，单据行仍能单独记跟进。
- [团队统计口径改变] → 「按负责人」的跟进数、今天要跟、逾期改为客户数（规格已写明）；跟进次数按记录计，一次覆盖多张单据算 1 次。

## Migration Plan

- 迁移 `V1.5.18__followup_record_items.sql`：建 `customer_followup_item`（带 `tenant_id`，归入 `TenantTables` 私有表），`CREATE TABLE IF NOT EXISTS`，可重复执行；不回填历史数据。
- 回退：代码回退后关联表不再被读取，可以保留；需要删除时 `DROP TABLE customer_followup_item`。
- 无新增配置项。
