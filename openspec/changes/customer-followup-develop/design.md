## Context

前三期已有的基础：
- `followup_item` 唯一键 `(tenant_id, board, target_type, target_id)`；第三期新增来源类型 6-客户，客户经营 board 5 使用；
- `FollowupSync.resync(customerId)` 依次推导商机成交、订单履约、售后评价、客户经营；
- 培育（`nurture_until`）、延后、今日待办、排序、作战卡、团队视图、AI 整理和回复都已就绪；
- 第一期 `CLOSE_*` 用到 5，第三期新增 6-不再是成交客户。

可以直接利用的：
- `CustomerService` 中商机登记时建客户的方法（`CustomerLeadCommand`），已含查重；
- 客户档案的 `potential_rating`、`customer_contact`（邮箱、WhatsApp、电话、职位类别）；
- 背调报告 `customer_background_report`：`status`（1-背调中、2-已完成、3-失败）、`priority`（1-立即重点跟进、2-正常跟进、3-低优先级、4-不建议跟进）、`score`、`key_finding`、`first_action`；
- 客户来源渠道字典已有 7-主动开发；
- `OpportunityServiceImpl` 的登记流程（建客户 + 建商机 + 阶段记录）。

## Goals / Non-Goals

**Goals:**
- 开发对象不另建一张「线索」表，直接用客户档案，背调、联系人、时间线全部复用。
- 阶段尽量从档案和背调推导，业务员主要做「记一次跟进」，另外只有「新建开发对象」「转商机」「停止开发」三个动作。
- 转商机后，客户在链路上自然进入商机成交，不丢上下文。

**Non-Goals:**
- 群发邮件、邮件追踪（打开率）、自动发送：系统只提醒和写草稿，发送仍在邮箱或 WhatsApp 里完成。
- 搜客工具、海关数据接入：找到公司以后才进系统。
- 公海：系统没有公海的概念，不新增；未成交且不在开发中的客户，在客户详情可以「加入主动开发」。
- ICP（目标市场）配置：SOP 的「目标市场」不在系统里维护，「为什么值得开发」由业务员填写。

## Decisions

### D1 开发对象 = 客户 + board 1 跟进对象
- `board = 1`、`target_type = 6`、`target_id = customer_id`。一个客户只有一行开发记录（唯一键保证），停止或转商机后关闭，重新开发时重新打开。
- 新增字段：`prospect_reason`（≤200）、`prospect_source`（1-Google、2-LinkedIn、3-展会名录、4-海关数据、5-老客户介绍、6-其他）、`touch_count`、`first_touch_date`、`replied_at`、`signal_text`（≤200）、`signal_at`、`stop_reason`（码值）、`stop_note`（≤200）。
- 结束原因新增 7-转商机、8-停止开发、9-已进入商机成交。

不复用 board 2 的「商机」对象：开发阶段商机还不存在，SOP 原则是「Account ≠ Opportunity」。

### D2 加入条件统一校验 `DevelopEligibility`
- `biz_grade = 0`；
- board 2 没有进行中的对象，也没有未结束的商机；
- 没有销售订单；
- board 1 没有进行中的记录。

新建开发对象、加入开发、重新开发都走这个校验，不满足返回 `DEVELOP_NOT_ELIGIBLE` 并说明原因。

### D3 新建开发对象复用登记建客户
新增接口 `POST /api/v1/followups/develop`：
- 先调用现有查重（名称 + 国家、邮箱、WhatsApp、电话）。有疑似重复时返回 `DEVELOP_DUPLICATE` 和候选客户，前端让业务员选「加入已有客户」或「仍然新建」（仍然新建沿用登记的「确认不是同一家」参数）；
- 新建客户走 `CustomerLeadCommand`，`sourceChannel = 7`，联系人名称为空时用公司名称占位（与登记一致，联系人名称必填的规则在开发对象里放宽：没有联系人时不建联系人行）。

**需要确认的点**：`CustomerLeadCommand` 当前要求联系人名称。开发对象常常还没找到联系人（D3 才找），所以联系人选填。实现时给建客户方法加一个不建联系人的分支，不改登记本身的校验。

### D4 阶段推导 `DevelopStageDeriver`
纯函数，输入：潜力星级、最近一份背调（状态、优先级）、是否有可联系的联系人、`touch_count`、`replied_at`、`signal_at`，以及当前阶段。按 spec 计算推导阶段，结果取 `max(推导阶段, 当前阶段)`，实现「只进不退」。

例外：D2「资格不足」之后如果业务员把潜力改到 ≥3 星，推导结果会往前走到 D3 或更后，这本来就是前进，不冲突。

「可联系的联系人」：`customer_contact` 未删除且邮箱、WhatsApp、电话任一不为空；或 `customer` 主联系人字段任一不为空（存量客户的主联系人还在 customer 表上）。

### D5 序列提醒 `DevelopCadence`
系统节奏下（`next_action_source = 1`）：
- D1、D3、D4、D7：当天；
- D2：背调中为空；资格不足为加入当天（系统一次性提醒，之后不再刷新）；
- D5：以 `first_touch_date` 为第 1 天，节点取 `sequence-days`（默认 1,4,9,16）中已到达的最后一个；第一个节点已过才进入 D5，所以从第 4 天开始；
- D5 超过最后一个节点：`suggestNurture = true`，下一步「建议转培育或停止开发」，日期为最后一个节点日（逾期显示）。

每个节点的建议文字来自 `sop_stage.default_actions`（与第一期报价后节点的机制相同），键为 `d5:4`、`d5:9`、`d5:16`。

### D6 记跟进的触达结果
`RecordFollowupRequest` 新增 `touchResult`（1-已发出触达、2-客户回复了、3-出现采购信号、4-没回复、5-明确拒绝）和 `signalText`。
- board 1 必填 `touchResult`，其他板块传了返回 `FOLLOWUP_STAGE_MISMATCH`；
- 1、2：`touch_count + 1`；1 第一次时写 `first_touch_date`；2 写 `replied_at`（只写第一次）；
- 3：写 `signal_at`、`signal_text`；
- 4：`no_reply_streak + 1`；
- 5：只保存，停止开发由前端接着打开弹窗完成（保持「记跟进」和「停止」是两个动作，取消停止时跟进照样已保存）。
- `situation` 位掩码新增 64-客户回复、128-采购信号、256-明确拒绝（`customer_followup.situation` 为 int，够用）。

### D7 转商机
- `OpportunityService.createForCustomer(customerId, firstContactDate, demandSummary)`：校验客户存在、没有进行中的商机；来源 7、阶段 S1；写阶段记录「登记商机（来自主动开发）」。不经过登记查重。
- 接口 `POST /api/v1/followups/items/{id}/convert`：同一事务内建商机、关闭开发记录（原因 7）、写系统记录，然后调用 `resync(customerId)`，商机成交出现商机行。
- 权限：`crm:followup:record` 加商机登记权限（沿用登记商机按钮的权限码）。

### D8 停止、重新开发、自动结束
- `POST /items/{id}/stop`：原因必填，「其他」必须填说明；关闭（原因 8），写系统记录。
- `POST /api/v1/followups/develop/{customerId}/reopen`：校验 D2 的加入条件，清空关闭字段，保留 `touch_count` 等，重新推导阶段。
- 自动结束：`DevelopFollowupSync.resync` 在 board 2 推导之后运行，发现客户已有进行中的 board 2 对象、商机或订单时关闭（原因 9）。它在 `FollowupSync.resync` 里排在客户经营之前，因为客户经营不依赖开发记录。

### D9 排序
主动开发没有经营等级。排序键：潜力星级倒序 → 背调优先级（1→4，未出结论排在 2 和 3 之间）→ 逾期天数倒序。今日待办里仍按第一期规则统一排，开发对象的经营等级视为未成交。

### D10 团队视图
口径见 spec。全部从 board 1 对象的字段算（`create_time`、`first_touch_date`、`replied_at`、`closed_at` + `close_reason`），不需要额外记录。按负责人列表增加一列。

### D11 AI
- `followup-reply` 新增意图 `cold_outreach`：输入加 `prospectReason`、`keyFinding`、`contactTitle`、`sequenceDay`、`nodeHint`；技能文件按 SOP：首封「身份 + 价值 + 低摩擦问题」，后续每封换新价值点，最后一封收尾。
- `followup-extract` 的 `situation` 枚举加 `replied`、`buying_signal`、`refused`，只在 board 1 保留；`buying_signal` 时额外输出 `signalText`（型号、数量、时间）。

### D12 前端
- `FlowCards`：主动开发开放，三组数量；`StageBar` 新增 D1–D7 与三组；
- `FollowupTable`：board 1 列配置；
- 作战卡：`DevelopCard`（背调结论、联系人、序列进度、AI 写开发信、转为商机）；
- 弹窗：`NewProspectModal`（含查重结果）、`ConvertOpportunityModal`（需求摘要、首次接触日）、`StopDevelopModal`；
- `RecordFollowupModal` 新增 `TouchQuestions`；
- 客户详情：跟进分区头部按状态显示「加入主动开发」「重新开发」或开发阶段；
- `TeamView`：开发指标。

## Risks / Trade-offs

- **[开发对象混在客户列表里]** 开发对象都是客户档案，客户列表会多出很多还没成交的公司。→ 客户列表已有「来源渠道」「经营等级」筛选，可以筛掉；这符合「客户是轴心」的原则，开发期间的背调和联系人以后不用再录。
- **[查重误判]** 新建开发对象时被判重复会打断。→ 与登记一致，可以「确认不是同一家，仍然新建」。
- **[阶段只进不退]** 业务员误选「客户回复了」无法退回 D5。→ 影响只是提醒节奏变为业务员日期，可以接受；如有需要，后续加「调整开发阶段」。
- **[序列节点和业务员节奏冲突]** 业务员定了日期后，系统节点不再覆盖。→ 与报价后提醒的规则一致：业务员日期优先，到期再记跟进后恢复。

## Migration Plan

1. `V1.5.13__customer_followup_develop.sql`：`followup_item` 新增开发字段；`customer_followup` 新增 `touch_result`、`signal_text`；系统参数 `sales.followup.develop.sequence-days` = `1,4,9,16`；字典 `followup_stop_reason`；回退语句在脚本末尾。
2. `V1.5.14__sop_develop_content.sql`：导入 D1–D7 的 `sop_stage`（board 1，含 `default_actions` 的序列节点建议），`ref_days` 按 SOP 默认值写入（团队视图「卡在哪一步」用）。回退：删除 board 1 的行。
3. 上线时不补生成开发对象：开发对象只能由业务员新建或加入。

回退：执行脚本末尾的回退语句，代码回到第三期即可；开发记录会被删除，已转出的商机保留。

## Open Questions

- 无。D3 中开发对象联系人选填，按上述方式处理，不改商机登记的校验。

## 实现时的调整（2026-11-05）

实现中与上文不一致、按建议直接处理的地方：

- **停止原因用代码常量**：没有建字典 `followup_stop_reason`，原因键（REFUSED、MISMATCH、NO_REPLY、UNREACHABLE、OTHER）固定在 `FollowupConstants`，字段注释列出取值。原因只有五个、和停止开发弹窗的分组一一对应，做成字典反而会被改出弹窗认不得的值。
- **疑似重复不提供「仍然新建」**：原型画了这个按钮，实现只给「把这个客户加入开发」和「打开客户」。查重沿用客户本身的 `CUSTOMER_DUPLICATE` 错误；开发对象重复多半就是同一家，直接加入更不容易产生两份档案。需要时再补。
- **D5 节奏的接管规则**：记跟进必填下一步和日期，业务员定的日期优先；这个日期已过、之后又到了新的序列节点（第 4 / 9 / 16 天）时，改回按序列提醒，提示换一个新价值点。
- **新增联系方式 LinkedIn**：`customer_followup.channel` 加取值 5-LinkedIn（V1.5.13 改字段注释），主动开发触达常用。
- **转商机可选负责人**：转商机弹窗可选负责人，默认客户负责人；所选负责人必须在当前用户的数据范围内。
- **列表加国家筛选**：主动开发列表顶部加国家下拉，便于按市场集中发开发信。
- **采购信号也算回复**：选「出现采购信号」时，如果还没有回复日期，同时写入回复日期，回复率口径因此包含直接出信号的客户。
- **背调结论「未出结论」不参与**：背调报告优先级 0 表示未出结论，排序与阶段推导只看优先级 1–4。
- **AI 输出字段**：编排服务返回的触达答案、采购信号字段为驼峰命名（`touchAnswer`、`signalText`），与前几期的输出约定一致。
- **新建开发对象可选事业部**（走查中发现）：公司有多个事业部、当前用户不属于任何事业部时，建客户要求选择归属事业部；新建开发对象弹窗与商机登记一样显示「所属事业部」，接口增加 `businessUnitId`。
- **客户详情记跟进选「明确拒绝」也接着停止开发**（走查中发现）：原来只在客户跟进页衔接，客户详情跟进分区补上同样的衔接。
- **团队视图「卡在哪一步」去掉客户经营空行**（走查中发现，第三期遗留）：客户经营阶段没有参考停留天数，后端本来就不返回，前端不再画这一行。
- **主动开发列表的价值列只显示背调结论和潜力星级**：开发对象都还没成交，不再显示「未成交」经营等级。
