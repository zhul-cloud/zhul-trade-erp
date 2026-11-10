## Context

- 今日待办接口 `GET /followups/today` 一次返回全部逾期和今天到期的客户。前端先显示 8 个，「展开」后一次显示全部。批量操作上限 50 个客户（`BATCH_MAX`），由 `followup-group-by-customer` 引入。
- 下一步提醒节奏在后端纯函数里计算：`Cadence`（商机报价后 / PI 后）、`DevelopCadence`、`AccountCadence`。节点天数取系统参数，租户可覆盖。SOP 阶段表 `sop_stage` 有 `ref_days`（参考停留天数）和 `default_actions`（节点 → 默认动作文字）。
- 阶段推进：
  - 老客户的单据由 `OrderFollowupSync` 等按单据推导。
  - 新客户随商机阶段。
  - 记跟进的答案（客户情况、收货 / 使用 / 触达 / 客户经营答案）由各 `*StageRecorder` 推进，只前进不回退。
- 商机详情已有手动改阶段：`OpportunityServiceImpl.changeStage`，可切换到任意进行中阶段，写阶段记录 `ACTION_CHANGE`，再调用 `followupSync.customerChanged`。
- 跟进记录 `customer_followup` 已有 `stage_before`、`stage_after`、`record_type`（1 用户 / 2 系统）。

## Goals / Non-Goals

**Goals:**
- 四项改动都复用现有的记跟进、批量跟进链路，不另起一套写入逻辑。
- 推荐日期只在后端算一处，前端只按客户情况做简单修正。

**Non-Goals:**
- 不改商机详情页的手动改阶段（它仍可切到任意进行中阶段），只在跟进页加受限的「调整阶段」。两边规则是否统一，另行讨论。
- 快速跟进结果先写死在代码里，不做租户可配置。
- 今日待办不改为接口分页。

## Decisions

### D1 今日待办分批加载放在前端
接口仍一次返回全部客户，前端每次多显示 20 个。
- **理由**：单人待办通常是几十到一两百个客户，接口本身不慢，慢的是一次渲染全部卡片。顶部计数也可以直接沿用现有数据。
- **备选**：接口分页（`page`、`page_size`）。要另算各分组计数和「今天已跟进」，改动大，等单人待办上千时再做。

全选的范围是「已加载的可勾选客户，取前 50 个」。选中状态仍用现有的 `picked`（按客户 ID），全选只是批量写入。

### D2 推荐日期由后端算，随跟进对象返回
`FollowupItemVO` 增加 `suggestDate`、`suggestReason`，按 spec「下一步日期推荐」的表格计算：
- 报价后、PI 后、开发 D4/D5、客户经营：复用对应 Cadence，取「今天之后的下一个节点」。现有 `Cadence.of` 取的是已到达的最后一个节点，需要加一个取下一个节点的方法，不改原方法。
- 其他阶段：今天 + `ref_days`，限制在 1–14 天；没有或为 0 时 3 天。

前端修正：
- 勾了「在谈条件」用明天。
- 勾了「没回复」取推荐日期与 3 天后中较早的一个。
- 多张单据取最早的推荐日期。
- 用户点过日期后锁定，不再自动变。

**备选**：前端自己按阶段算。但节点天数是租户可改的系统参数，前端拿不到，会算错。

### D3 调整阶段的判断段写在后端常量里
`FollowupConstants` 增加判断段：`S1,S2`、`S4,S5,S6`、`D4,D5,D6`（实现时去掉了 `A1,A2`、`G4,G6`：这两段的阶段由签收、订单完成、联系记录推导，手动改后下一次同步就会改回）。接口为 `POST /followups/items/{id}/stage`，参数为 `toStage`、`reason`（`HISTORY` / `LAGGING` / `CHANGED` / `OTHER`）、`note`（选 OTHER 时必填）。服务端校验当前阶段和目标阶段在同一段内。

处理方式：
- **跟进对象属于进行中商机**：调用商机 `changeStage`，备注写「手动调整：理由」。阶段同步由它触发的 `customerChanged` 完成，避免两处各改一遍。
- **老客户单据**：直接改 `stage_code`，重置 `stage_since`。
- 两种情况都写一条 `customer_followup`：`record_type = 1`，标题为「调整阶段：S4 → S6」，`stage_before`、`stage_after` 填好，不改下一步。

**手动后退和单据推导冲突的处理**：老客户的报价单由同步任务按单据推导，S4/S5/S6 中的 S5、S6 本来就来自记跟进的答案，推导只保证不低于 S4，所以后退到 S4 或 S5 不会被同步改回。实现时要用测试确认这一点（见 tasks 2.4）。

**「到下一阶段还差什么」**：前端按阶段写死一张映射表（S2 去建询盘、S3 去报价、S6 去开 PI，F 阶段说明系统怎么推进，A3/A4 去售后单）。文字与 SOP 的 `auto_hint` 一致。

### D4 快速跟进复用记跟进与批量跟进
- 结果定义放在后端 `QuickResult` 枚举（阶段 → 结果码 → 对应的 `situation` / 各类 answer、对日期的修正），前端从接口取 `GET /followups/quick-results`，避免两边各写一份。
- **单个快速跟进**：前端组装成一次 `RecordFollowupRequest`。下一步文字取 SOP 默认动作，日期取推荐日期，`quick = true`，`contactDate` 默认今天。走现有 `POST /followups/records`。
- **批量快速跟进**：新接口 `POST /followups/quick-batch`，参数为若干「阶段组 → 结果码 + 客户与单据」加一个 `contactDate`。服务端逐客户展开成记跟进请求，复用 `FollowupBatchService` 的逐客户独立处理和结果汇总；幂等键沿用批量跟进的做法。
- **为什么不直接扩展现有批量跟进**：现有批量跟进要求所有客户共用同一个下一步文字和日期，而快速跟进按阶段各不相同；硬塞进去会让原接口的参数含义变复杂。

### D5 数据库
`customer_followup` 增加两列：
- `quick tinyint(2) NOT NULL DEFAULT 0 COMMENT '是否快速记录（0-否、1-是）'`
- `contact_date date NULL COMMENT '实际沟通日期，快速记录时填写，NULL 表示同记录时间'`

团队视图在现有跟进次数统计上，加一个 `quick = 1` 的计数。

## Risks / Trade-offs

- [快速记录让跟进次数虚高] → 记录打标记，团队视图单独显示快速次数，主管能看出来。
- [手动调整阶段被用来躲「停留过久」提醒] → 只开放判断段；每次调整都留记录和理由，团队视图的阶段停留统计不受影响。
- [前端分批加载在客户特别多时仍慢] → 先观察。超过几百个时再改接口分页（D1 备选）。
- [商机页可自由改阶段，跟进页受限，两边规则不一致] → 本次不动商机页，在汇报里说明，后续统一。

## Migration Plan

- 迁移 `V1.5.21`：给 `customer_followup` 加两列，历史记录 `quick = 0`、`contact_date = NULL`。
- 回退：删除这两列；前端隐藏快速跟进入口即可。
- 无配置项变化。
