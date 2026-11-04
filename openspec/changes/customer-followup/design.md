## Context

现状：
- **商机**（`crm/opportunity`）只记新客户获客，一个客户最多一条。
  - 阶段配置在 `opportunity_stage`（tenant 0）；
  - 创建询盘时自动推进到 S3，生成报价单时自动推进到 S4；
  - 赢单需要手动标记，且只能在 S7。
- **老客户的新需求**走询盘单，没有商机。
- **报价单**状态：草稿 / 已发送 / 部分成交 / 已成交 / 未成交 / 已作废，有 `sent_at`、`total_amount_cny`。
- **PI**：
  - 有 `receipt_status`（未付款 / 待到账 / 部分到账 / 已到账 / 无需收款）、`received_amount`、`sent_at`；
  - 收款确认在 `PaymentReceiptServiceImpl.confirm`；
  - PI 明细 `pi_item.quotation_id` 记录来自哪张报价单。
- **询盘明细**只有数量；采购报价（`sourcing_quote`）有单价。
- **客户**有经营等级 `biz_grade`（每晚 02:30 重算）和潜力等级 `potential_rating`；字段来源记在 `customer_field_source`。
- **AI** 走编排服务异步任务，按 skillId 回调 `AiTaskResultHandler`，现有询盘解析、客户背调两个处理器。

约束：
- 多租户：新表进 `TenantTables`，定时任务用 `TenantScope.platform` 或 `TenantContext.runAs`；
- 时间统一存北京时间；
- 列表页规范对工作队列做例外。

## Goals / Non-Goals

**Goals:**
- 商机成交板块的跟进对象全部由单据自动生成和移出，业务员只做「记一次跟进」。
- 新客户、老客户在同一个板块里，用同一套 S 阶段展示，商机规则和统计口径不变。
- 今日待办和各计数在几百个跟进对象的规模下秒开。
- 记跟进的 AI 整理失败时，不影响手工记录。

**Non-Goals:**
- 订单履约、售后评价、客户经营、主动开发四个板块本期不做，只在链路上显示「即将上线」。
- SOP 内容的页面编辑：本期用迁移导入，后续做维护页。
- 消息推送（站内信、企业微信）不做，提醒只体现在今日待办和计数上。
- 不做跟进 KPI 和统计报表。

## Decisions

### D1 跟进对象落一张表 `followup_item`，由单据事件维护，每晚核对兜底
每个跟进对象一行，字段如下：

| 类别 | 字段 |
| --- | --- |
| 来源 | `target_type`（1-商机、2-询盘、3-报价单、4-PI）、`target_id` |
| 归属 | `customer_id`、`owner_id`、`board`（本期固定 2-商机成交） |
| 阶段 | `stage_code`、`stage_group`（报价前 / 报价后 / PI 后） |
| 跟进状态 | `blocker_code`、`next_action`、`next_action_date`、`next_action_source`（1-系统、2-业务员）、`snooze_count` |
| 上次跟进 | `last_followup_id`、`last_followup_at`、`no_reply_streak`、`feedback_seen` |
| 培育 | `nurture_reason`、`nurture_until` |
| 排序 | `amount_cny`（排序用金额） |
| 结束 | `closed_at` |

唯一键 `(tenant_id, target_type, target_id)`。

维护方式：
- 报价单发送、作废、成交或未成交，PI 发送、关闭、作废、收款确认，询盘创建和生成报价，商机阶段变化时，都调用 `FollowupSync.onXxx(...)`，在原事务内 upsert 或关闭对应行。
- 每晚 02:40（客户等级重算之后）的 `FollowupReconcileJob` 按单据全量核对一遍：修正遗漏、移出已结束的、到期的培育重新激活、按系统节奏刷新下一步。

备选方案：每次查询时用 UNION 实时从报价单、PI、询盘拼。不采用，原因：
- S5/S6、卡点、下一步、培育这些状态本来就要有地方存；
- 板块计数、今日待办和跨单据排序用实时 UNION 会很重。

### D2 新老客户的阶段来源
- **有进行中商机**的客户：`stage_code` 跟商机走。商机阶段变化（手动、自动）都同步到该客户所有未结束的跟进对象。
- **没有商机**的客户（老客户），按单据推导：
  - 询盘：S3；
  - 报价单：S4，或业务员勾选推进后的 S5/S6，存在跟进对象上；
  - PI：S7。
  - 只前进不回退。
- 一个客户可能同时有多个跟进对象（两张报价单加一张 PI），各自独立出现在列表中。客户数按去重后的客户计。

### D3 「报价前」只覆盖两类
新客户在 S1–S3 的商机，以及老客户已登记、还没报价的询盘。询盘转报价单时，询盘的跟进对象关闭，跟进记录挂到报价单的跟进对象上，记录本身不搬，按客户都能查到。

### D4 同一来源询盘只保留最新报价单
报价单按 `(customer_id, 来源询盘集合)` 判断是否同一来源。新发送的报价单接替旧报价单的跟进对象：
- 旧行关闭；
- 新行继承卡点、下一步、阶段、无回复计数。

来源取报价单明细关联的询盘；没有关联询盘的报价单各自独立。

### D5 PI 接替报价单
PI 发送时，明细 `quotation_id` 指向的报价单的跟进对象关闭，新建 PI 的跟进对象，并继承卡点和下一步。PI 收款状态变为「已到账」或「无需收款」，或者 PI 关闭、作废时，关闭 PI 的跟进对象。

### D6 提醒节奏
`next_action_source = 1`（系统）时，系统按节奏计算 `next_action_date`：
- 报价后：发送日 + 1、3、7、14 天中下一个未过去的节点；14 天之后不再生成系统节点，改为「建议转培育」。
- PI 后：有预计付款日（`expected_pay_date`，记跟进时填写，存在跟进对象上）时，用这个日期；过期后每次按 + 2 天顺延。没有预计付款日时，发送日 + 2 天，之后每 2 天。

业务员记跟进后，`next_action_source = 2`，按业务员的日期提醒。

节奏参数放 `sys_config`，platform 行加租户覆盖（与现有读取方式一致）：
- `followup.quote.days = 1,3,7,14`
- `followup.pi.interval-days = 2`
- `followup.nurture.default-days = 60`
- `followup.nurture.quote-days = 14`
- `followup.nurture.no-reply-times = 3`

系统提醒的文字按阶段和节点取 SOP 的默认动作（`sop_stage.default_actions`）。

### D7 客户价值排序在 SQL 中完成
列表和今日待办都按以下顺序排序：
1. 经营等级：`biz_grade`，0 表示未成交，排最后；
2. 潜力等级：`potential_rating DESC`，0 表示未评，排最后；
3. 金额档：由 `amount_cny` 计算，取值 0–3；
4. 逾期天数；
5. 下一步日期。

`amount_cny` 在同步时写入：
- PI：`total_amount_cny`；
- 报价单：`total_amount_cny`；
- 询盘：Σ(明细数量 × 已有采购报价的最低单价，按采购报价币种的汇率折成人民币)，没有报价的明细不计。

分档阈值 20 万、5 万、1 万也放系统参数 `followup.amount-tiers`。客户等级每晚变化后，不需要回写跟进对象，排序时 JOIN `customer` 读取（单表加客户一张，不超过 3 张表）。

精度：金额档只用于比较，不做舍入存储；`amount_cny` 为 DECIMAL(18,2)，沿用单据的本位币金额，不重新换算。

### D8 跟进记录 `customer_followup`
| 类别 | 字段 |
| --- | --- |
| 关联 | `customer_id`、`followup_item_id`、`target_type`、`target_id`、`opportunity_id` |
| 情况 | `situation`（位掩码：1-给了反馈、2-在谈条件、4-没回复）、`blocker_code` |
| 下一步 | `next_action`、`next_action_date` |
| 内容 | `channel`、`raw_content`（TEXT）、`ai_summary`、`ai_task_id` |
| 回溯 | `stage_before`、`stage_after`、`expected_pay_date` |

另有 `operator_id`（记录人）。只能新增，不修改不删除，便于追溯。

### D9 记一次跟进的事务边界
`FollowupService.record` 在一个 `@Transactional` 中完成：
1. 写入跟进记录；
2. 更新跟进对象；
3. 有商机时，调用 `OpportunityService.autoAdvance(customerId, target, "记跟进，自动推进")`；
4. 调用 `CustomerProfileService.applyFollowupSuggestions(...)` 写入勾选的档案字段，来源记为跟进（`customer_field_source.source` 新增枚举值 4-跟进记录，`source_ref` 记跟进记录 id）。

任一步失败整体回滚，这一步是业务员的主动操作，要保证一致。

由单据事件触发的同步（D1）和商机的自动推进、自动赢单（opportunity 规格）放在原单据事务里，但用 try/catch 吞掉跟进侧的异常，只记 WARN 日志，保证跟进出错不影响报价、PI、收款本身；漏掉的由每晚核对补上。

### D10 收款自动赢单的关联方式
商机和 PI 没有直接关联，按客户关联：PI 收款确认后 `received_amount > 0` 时，取该客户进行中的商机（最多一条）赢单。原因：一个客户最多一条商机，客户第一次付款就是获客成功，与 SOP「约定款项到账 = WON」一致。不再要求「S7 才能赢单」，直接补一条推进到 S7 的阶段记录再赢单。

`OpportunityStageRules.checkClose` 对 `CATEGORY_WON` 一律拒绝手动关闭，自动赢单走单独的方法，不经过手动校验。

### D11 AI 整理
- 编排服务新增技能 `followup-extract`（`scripts/ai-orchestrator/skills/followup-extract/SKILL.md`）。
  - 输入：客户名称、当前阶段、报价或 PI 概要、档案缺项清单、粘贴原文（取最后 20,000 字）；
  - 输出 JSON：`summary`、`situation[]`、`blocker`、`next_actions[≤2]`、`profile_suggestions[{field, value, evidence}]`。
- 后端新增 `FollowupExtractResultHandler`，把结果写到 `ai_task` 结果上，不直接改业务数据。
- 前端提交后轮询任务状态（2 秒一次，60 秒超时），拿到结果后预填表单；最终以业务员保存时提交的值为准。
- 「AI 写一条英文回复」复用同一编排服务，新增技能 `followup-reply`（基于「烛龙英文回复」改写的系统版），输入客户上下文、卡点和作战卡应对要点，输出英文草稿；同步等待，超时 30 秒。

两个技能都按租户的 AI 配置调用；没配置时按钮置灰并提示。用量沿用 `ai_task` 的 token 记录。

### D12 SOP 内容存储
- `sop_stage`：`board`、`stage_code`、`name`、`goal`、`checklist`（JSON 数组）、`gate_text`、`auto_hint`、`default_actions`（JSON，按提醒节点）、`example_dialog`。
- `sop_blocker`：`code`（PRICE、DELIVERY…）、`name`、`playbook`（JSON 数组）、`sort_order`。

两张表都是平台数据，`tenant_id = 0`，共享只读（SHARED_READ），租户以后可覆盖。迁移脚本从《商机成交 SOP》Excel 整理成 INSERT，本期只导入商机成交的 S1–S7 和卡点字典。

输单原因用 SOP 卡点：
- 保留现有 `LOST_REASONS` 码值：11 价格、12 交期、13 无货源、14 客户取消、15 选择了竞争对手、19 其他；
- 新增 16 技术、17 付款、18 信任、20 预算、21 内部审批、22 项目延期、23 无回复。
- 每个卡点码值映射到 `sop_blocker.code`。

报价单的未成交原因已经是租户可维护的字典（`quotation_lost_reason`），不改。

### D13 前端
- 新页面 `src/pages/sales/followup/`，结构如下：
  - 顶部链路卡片：`FlowCards`；
  - 阶段导航：`StageBar`；
  - 列表：`FollowupTable`，按分组切换列配置；
  - 作战卡：`BattleCard`；
  - 今日待办：`TodayList`。
- 记一次跟进弹窗 `RecordFollowupModal` 做成可复用组件，客户详情和商机详情也用它。
- 客户详情主栏最上面新增「跟进」分区（K9 已确认，不做页签），可收起 / 展开，收起状态按用户存在浏览器本地；时间线组件 `FollowupTimeline`。
- 商机详情去掉「标记赢单」，S7 主按钮改为「记一次跟进」；「标记输单」原因换成 SOP 卡点列表，「转入培育」默认勾选。
- 列表每次加载 20 行，用 `LoadMore`（复用 `inquiry/shared/BrandGroups` 的组件，必要时提到公共组件目录）。

### D14 接口
统一前缀 `/api/v1/followups`：

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| GET | `/overview` | 链路卡片计数 |
| GET | `/today` | 今日待办 |
| GET | `/items` | 列表：`board`、`group`、`stage`、`nurture`、`ownerScope`、`page`、`page_size` |
| POST | `/items/{id}/snooze` | 延后 |
| POST | `/items/{id}/nurture` | 转培育 |
| POST | `/items/{id}/activate` | 激活培育 |
| POST | `/records` | 记一次跟进；带 `idempotency_key` 防重复提交 |
| POST | `/extract` | 提交 AI 整理，返回 taskId |
| GET | `/extract/{taskId}` | 查询 AI 整理结果 |
| POST | `/reply-draft` | AI 写英文回复 |
| GET | `/sop/{board}/{stage}` | 作战卡内容 |
| GET | `/customers/{customerId}/records` | 客户的跟进记录 |

返回的金额字段都带 `currency_code`。

## Risks / Trade-offs

- **事件遗漏导致列表与单据不一致**：单据状态变化的入口较多（发送、修改新版本、批量操作、迁移数据）。缓解：每晚核对任务兜底；在核对任务中记录修正的条数，异常增多时排查遗漏入口。
- **取消手动赢单影响老数据和习惯**：现有 S7 商机靠手动赢单，上线后只能等收款。缓解：上线时把「已有确认收款、但商机仍未赢单」的客户由迁移脚本一次性补赢单；PR 说明里写明。
- **按客户赢单可能误判**：客户第一次付款是给别的 PI 时，也会让商机赢单。业务上一个客户最多一条获客商机，第一次付款即获客成功，可以接受。
- **AI 整理质量不稳定**：只做预填，所有字段都可改，最终以业务员保存的为准；档案补充默认只勾空字段，不覆盖已有值。
- **询盘预估价值偏低**：没有采购报价的明细不计，新询盘多在最低档。由于排序先比经营等级和潜力，影响有限。
- **迁移与回退**：迁移脚本从 V1.5.5 起。
  - 回退：删除 4 张新表和 `followup.*` 系统参数，`customer_field_source.source = 4` 的行改为 1（人工），输单原因新码值保留（文字照常显示）。
  - 回退后商机赢单恢复手动，需同时回退代码。
