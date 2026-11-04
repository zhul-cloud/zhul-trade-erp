## Context

前两期（`customer-followup`、`customer-followup-fulfillment`）已有的基础：
- `followup_item` 一行一个跟进对象，唯一键 `(tenant_id, board, target_type, target_id)`；`target_type` 已用到 5-销售订单；
- `FollowupSync.resync(customerId)` 推导商机成交，再调用 `OrderFollowupSync.resync` 推导订单履约和售后评价；
- `FollowupReconcileJob` 每晚 02:40 和启动完成后全量核对；客户经营等级由 `CustomerNightlyJob` 在 02:30 重算，顺序已经满足；
- 记跟进 `FollowupService.record` 按板块校验阶段字段；`OrderStageRecorder` 处理履约和售后；
- 团队视图 `FollowupTeamService` 支持本周、上周、本月；
- SOP 内容在 `sop_stage`、`sop_blocker`，迁移到 V1.5.10。

现有数据中可以直接利用的：
- `customer.biz_grade`（0-未成交、1-S、2-A、3-B、4-C）、`biz_grade_score`、`rfm_json`、`profile_mask`、`potential_rating`；
- 系统参数 `customer.grade.follow-days`（7,14,30,60）；
- `sales_order`：`sales_date`、`status`、`after_sales_id`、`original_so_id`、`migrated`、`total_amount_cny`、`completed_at`；`sales_order_item.brand`、`category`、`model`、`amount_cny`；
- 前端 `NewInquiryModal` 已支持传入 `customerId`。

## Goals / Non-Goals

**Goals:**
- 客户经营板块的跟进对象、阶段、提醒、采购规律、沉睡全部由现有数据推导；业务员只做「记一次跟进」，另外只多一个「设为战略客户」。
- 复用前两期的同步、核对、排序、今日待办、作战卡、团队视图，不另起一套。
- 几百个成交客户的规模下，核对一次在一分钟内完成。

**Non-Goals:**
- 主动开发板块（第四期）。
- 采购规律的机器学习、季节性分解：只用间隔中位数，样本少时如实标注。
- 交叉销售的商品级推荐：只推荐品类，不推荐型号和价格。
- 年度计划的审批、按季度拆分目标、多年对比。
- 老客户新需求建商机：沿用已定口径，老客户新需求走询盘，不建商机。

## Decisions

### D1 来源类型「客户」，一个客户一行
- `target_type` 新增 6-客户，`target_id = customer_id`，`board = 5`。唯一键已含板块，无需改。
- 生成条件：`biz_grade > 0`、未删除、启用。不满足时关闭（`close_reason` 新增 6-不再是成交客户），重新满足时重新打开，保留原字段。
- 新增 `AccountFollowupSync.resync(customerId, today)`，由 `FollowupSync.resync` 在订单同步之后调用。客户经营要读其他板块的结果（是否有进行中的对象），所以必须最后执行。

### D2 阶段取值顺序
按 spec 顺序取第一个满足的：G5 → G7 → G6 → G4。判断依据：
- G5：同客户 board 2 有 `closed_at IS NULL` 的对象；或 `account_need = 1`（记跟进勾「客户有新需求」时置 1，该客户新建询盘后清零）；
- G7：`customer.is_strategic = 1`；
- G6：`account_cross = 1`（勾「聊了新品类」时置 1；勾「当前没有需求」或新建询盘时清零）。

不做「只进不退」：经营是循环，询盘结束后回到 G4 / G7 是正常的。

### D3 采购规律 `PurchasePattern`
纯函数，输入一个客户的订单日期与金额列表，输出周期、预计采购日、文字。
- 订单取近 3 年、`status` 不是已取消、`after_sales_id = 0 AND original_so_id = 0`（不含售后补发），`migrated` 计入；
- 按 `sales_date` 排序，与上一次采购相隔 ≤7 天的并入上一次；
- 次数 ≥3：周期 = 相邻间隔的中位数（偶数个时取中间两个的平均，向下取整到天）；预计采购日 = 最近一次 + 周期；
- 品牌、品类：近 12 个月 `amount_cny` 合计，各取前 3，空值不计。

结果存到跟进对象：`pattern_cycle_days`、`pattern_next_date`、`pattern_text`、`pattern_brands`（≤200 字）。每次 resync 重算（订单变化会触发 resync）。不缓存：单客户订单数在百级以内。

### D4 提醒节奏 `AccountCadence`
`next_action_source = 1`（系统）时每天重算，取最早的：
- 等级：`last_contact + follow-days[grade]`；`last_contact` = 该客户所有跟进记录（`customer_followup`，含系统记录）的最大 `create_time` 日期，没有时取最近一单 `sales_date`；
- 预计采购：`pattern_next_date - 30`；
- 沉睡：`dormant_since` 起每 60 天一个节点，取已到达的最后一个；
- 季度回顾（战略客户）：`last_review_date + 90`，没有时取设为战略客户的日期 + 90。

触达理由写入 `account_reason`（≤100 字），作战卡和列表的「经营建议」显示它。

**暂停**：同客户在 board 2、3、4 有进行中的对象时，`next_action_date = NULL`，`next_action` 写「在 xx 跟进中」（取第一个进行中的板块名）。不改 `next_action_source`，暂停结束后自动恢复。

业务员记跟进后 `next_action_source = 2`，按业务员日期；该日期过去且业务员又记过跟进后照旧，日期过去而没有新记录则保持逾期（与第一期一致）。

### D5 沉睡
- `dormant_since`：满足「最近一单 > 180 天」且「近 90 天没有新询盘 / 报价 / 商机」时，写入首次满足的日期；不满足时清空。
- 激活计数：清空 `dormant_since` 时写一条系统跟进记录「沉睡 n 天后激活」（`record_type = 2`，标题固定前缀），团队视图按这条记录计数，不新增表。

### D6 交叉销售 `CrossSell`
每次打开作战卡时按需计算（不落库）：
1. 该客户买过的品类集合 C（全部未取消订单，`category` 去空白）；
2. 本租户买过 C 中任一品类的其他客户集合 P；
3. 统计 P 中每个客户买过、但不在 C 里的品类，按客户数倒序，取 ≥2 的前 2 个。

一次两条聚合 SQL（按品类分组、按客户去重），走 MyBatis，经租户拦截器。客户数上千时也是毫秒级。

### D7 战略客户与年度计划
- `customer` 新增 `is_strategic tinyint(1)`、`strategic_at datetime`、`last_review_date date`。改动走新接口 `PUT /api/v1/followups/accounts/{customerId}/strategic`，只改这三个字段，记操作日志；不改客户档案的编辑接口。
- 新表 `customer_annual_plan`：`customer_id`、`plan_year`、`target_amount`（DECIMAL(18,2)，本位币 CNY，`currency_code` 固定 CNY）、`focus`（≤200）、`note`（≤500），唯一键 `(tenant_id, customer_id, plan_year)`，软删除。归类 `TenantTables` 私有。
- 今年已成交 = 当年 `sales_date` 的未取消订单 `total_amount_cny` 合计（不含售后补发）。完成比例只在接口里算，`HALF_UP` 取整，不存。
- 季度回顾：记跟进勾「这次是季度回顾」时更新 `last_review_date = 今天`。

### D8 记一次跟进
`RecordFollowupRequest` 新增 `accountAnswer`（1-有新需求、2-聊了新品类、3-当前没有需求、4-没回复）、`crossCategory`（≤50）、`noNeedReason`（码值）、`quarterReview`（布尔）。
- board 5 必填 `accountAnswer`；其他板块传了返回 `FOLLOWUP_STAGE_MISMATCH`；
- 没回复复用 `no_reply_streak`；
- `situation` 位掩码新增 8-新需求、16-新品类、32-当前没有需求，供时间线和 AI 整理使用；
- 「有新需求」的前端行为：保存成功后打开 `NewInquiryModal`（带 `customerId`），询盘创建后由现有询盘钩子触发 resync，`account_need` 在 resync 中看到新询盘就清零。

### D9 团队视图
在 `FollowupTeamService` 的结果区块新增经营指标，口径见 spec。实现方式：
- 应联系 / 按时：本期内 `next_action_date` 落在期间的 board 5 对象，用「到期日」与该对象下一条用户跟进记录的时间比较。为了不在每次请求里回放历史，记跟进时在跟进记录上写 `due_date`（记录当时对象的下一步日期），按时 = `create_time::date <= due_date`；应联系 = 期间内到期的对象数（含没跟的）。
- 老客户新询盘：本期 `customer_inquiry.create_time` 在期间内且客户 `biz_grade > 0`；
- 老客户成交：本期到账（`receipt_status` 变为已到账的时间，沿用第一期「到账成交」的口径），且下单时客户已有更早的订单；
- 沉睡 / 激活：见 D5。

### D10 AI
- `followup-reply` 新增意图 `account_touch`：输入加 `reason`、`pattern`、`lastOrder`（品牌、型号、数量）、`crossSell`；技能文件补充「不只问 Any new inquiry，围绕上次项目给一个具体理由」。
- `followup-extract` 的 `situation` 枚举加 `new_need`、`cross_sell`、`no_need`，`sanitize` 只在 board 5 保留这三个值，其他板块丢弃。
- 字段命名沿用驼峰（与已实现的 `nextActions` 等一致）。

### D11 前端
- `FlowCards` 开放客户经营；`StageBar` 新增 G4–G7、沉睡、培育中、全部老客户。
- `FollowupTable` 增加 board 5 的列配置；「建询盘」操作打开 `NewInquiryModal`。
- 作战卡新增 `AccountHeader`（G1–G3 结果）、交叉销售建议、战略客户与年度计划（`AnnualPlanModal`）。
- `RecordFollowupModal` 新增 `AccountQuestions`。
- `TeamView` 结果区块和按负责人表格增加经营指标。

## Risks / Trade-offs

- **[采购规律样本少]** 大部分客户只有 1–2 单。→ 如实显示「数据不足 / 样本少」，不推算日期，提醒退回按等级节奏。
- **[和其他板块重复提醒]** 老客户在履约、售后时又收到经营提醒会很烦。→ D4 的暂停规则：有任一进行中的对象就暂停。
- **[上次联系日的口径]** 系统记录（如「发送 PI」）也算联系，可能让经营提醒推迟。→ 这些都是真实和客户的接触，算联系符合 SOP「每次接触都要有价值」的本意；如果反馈不对，再改成只算用户记录。
- **[交叉销售在小租户没意义]** 客户少时几乎推荐不出来。→ 至少 2 个客户才推荐，推荐不出就不显示。
- **[团队视图口径变化]** 「按时」依赖新写入的 `due_date`，上线前的记录没有。→ 只统计上线后的到期，文档说明。

## Migration Plan

1. `V1.5.11__customer_followup_account.sql`：
   - `followup_item` 新增 `pattern_cycle_days`、`pattern_next_date`、`pattern_text`、`pattern_brands`、`account_reason`、`account_need`、`account_cross`、`dormant_since`；
   - `customer_followup` 新增 `account_answer`、`cross_category`、`no_need_reason`、`quarter_review`、`due_date`；
   - `customer` 新增 `is_strategic`、`strategic_at`、`last_review_date`；
   - 新建 `customer_annual_plan`；
   - 系统参数 `sales.followup.account.ahead-days`=30、`dormant-days`=180、`dormant-quiet-days`=90、`dormant-interval-days`=60、`review-days`=90；
   - 回退语句写在脚本末尾。
2. `V1.5.12__sop_account_content.sql`：导入 G4–G7 的 `sop_stage` 行（board 5），`ref_days` 为空（客户经营不按停留天数判断卡住）。回退：删除 board 5 的行。
3. 上线后首次启动的核对任务为全部成交客户生成客户经营对象，提醒从当天起算（沿用 `bootstrap`）。

回退：执行脚本末尾的回退语句，代码回到第二期即可；客户经营对象和年度计划会被删除。

## Open Questions

- 无。阈值（提前天数、沉睡天数、回顾间隔）都做成系统参数，上线后按反馈调整。
