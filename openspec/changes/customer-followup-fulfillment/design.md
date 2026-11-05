## Context

第一期（变更 `customer-followup`，分支 `feat/customer-followup`）已有的基础：
- `followup_item` 一行一个跟进对象，唯一键 `(tenant_id, target_type, target_id)`；
- `FollowupSync.resync(customerId)` 按单据重新推导一个客户的全部跟进对象；
- `FollowupHooks` 由各单据服务在原事务里调用，自身不抛异常；
- `FollowupReconcileJob` 每晚 02:40 和启动完成后全量核对；
- `sop_stage`、`sop_blocker` 保存 SOP 内容，迁移到 V1.5.8。

现有单据中可以直接利用的：
- **销售订单型号行 `progress_code`**：已经由采购（已下单）、入库（已入库）、出库（已交货代）、出运（已出运）自动推进（`OrderPurchaseProgress.sync`），手动步骤来自字典 `sales_order_status`。订单「客户已收货」由 `SalesOrderService.complete` 置为已完成。
- **采购订单**：`expected_ship_date`（下单时必填），采购行带 `so_id`、`so_item_id`；`ShipProgress` 已有「逾期未发」的判断口径。
- **仓库**：
  - 到货差异 `receiving_discrepancy.status`（1-待处理）；
  - 实拍任务 `shoot_task`（1-待拍摄、2-已完成，带 `so_id`）；
  - 供应商发货 `supplier_shipment.forwarder_id` 不为空表示直发货代。
- **物流**：
  - 销售出库单 `outbound_order`：1-待打包、2-已打包、3-已交货代、4-已撤回，`logistics_id` 指向出运单；
  - 出运单 `logistics_shipment`：`carrier` 为自由文本，`shipped_date`、status 2-已出运。
- **售后单 `after_sales`**：`so_id` 可为空；status 1-处理中、2-待客户确认、3-已结案、4-已撤销；自带 `next_action`、`next_update_at`、`due_at` 提醒。
- 系统没有物流轨迹，也没有签收数据。`transit_time` 表是快递从供应商到福州仓库的时效，与国际运输无关，不复用。

## Goals / Non-Goals

**Goals:**
- 两个新板块的跟进对象、阶段、异常、预计送达全部由单据推导，业务员只做「记一次跟进」。
- 复用第一期的同步、核对、排序、今日待办、作战卡机制，不另起一套。
- 团队视图在几百个跟进对象、十几个负责人的规模下一次请求返回。

**Non-Goals:**
- 物流轨迹查询、承运商签收回传：以后单独立项，届时把估算换成真实节点。
- 客户经营、主动开发板块：仍显示「即将上线」，售后结束的客户暂时只移出列表。
- 团队视图的导出、趋势图、按周的历史快照：只做实时计算。
- 售后单本身的流程变化：只增加「案例沉淀」字段和同步通知。

## Decisions

### D1 新来源类型「销售订单」，唯一键加上板块
- `target_type` 新增 5-销售订单。订单履约（board 3）和售后评价（board 4）的跟进对象都指向同一张销售订单。
- 唯一键改为 `(tenant_id, board, target_type, target_id)`。
- 一张订单同一时刻最多有一个未结束的跟进对象：转入售后时，履约行关闭（`close_reason` 1-单据完成）；售后结束后又登记售后单时，重新打开售后行。

备选方案：售后评价另用 target_type 6。不采用，原因是两种类型指向同一张单据，`docNo`、金额、客户都要重复处理，按板块区分更直接。

### D2 推导放在 `FollowupSync.resync`，按板块分别推导
`resync(customerId)` 的处理顺序：
1. 商机成交板块，保持现状；
2. 订单履约；
3. 售后评价。

后两个板块的推导写成独立的 `FulfilDeriver`、`AfterSalesDeriver`，输入为该客户的进行中订单及其关联单据，输出为期望的跟进对象集合，增删和继承沿用现有的 upsert 和关闭逻辑。单据事件统一调用 `FollowupHooks.orderChanged(soId)`（内部按 `customer_id` 做 resync），只需在各入口加一行。

| 事件 | 入口 |
| --- | --- |
| 销售订单创建、取消、部分取消、客户已收货 | `SalesOrderServiceImpl` |
| 采购单下单、取消 | `PurchaseOrderService`，按采购行的 `so_id` 去重后逐张通知 |
| 供应商发货登记、作废，入库 | 仓库服务 |
| 到货差异新建、处理 | 到货差异服务 |
| 实拍任务完成、跳过 | 实拍任务服务 |
| 销售出库单创建、撤回 | 出库服务 |
| 出运单出运、作废、调整货物 | 出运服务 |
| 售后单新建、状态变化、修改下一步 | `AfterSalesService` |

改动多为一行调用，失败只记 WARN，规则同第一期 D9。漏掉的入口由每晚核对补上。

### D3 履约阶段由型号进度推导，「部分」只用于展示
整单阶段按每个未全部取消的型号行计算，然后取最小值：

| 型号进度 | 阶段 |
| --- | --- |
| `PENDING_PURCHASE` | F2；订单没有任何已下单采购行，且该跟进对象没有业务员记录时为 F1 |
| `ORDERED` | F3 |
| `RECEIVED` | F4；在 1–3 状态的出库单上时为 F5 |
| `TO_FORWARDER` | F5 |
| `SHIPPED` | F6 |
| 字典里的手动步骤 | 按 `OrderProgress.rank` 找它前面最近的自动步骤，再按上面的规则映射 |

不经过采购的型号会直接从 `PENDING_PURCHASE` 跳到出库和出运，自然跳过 F2、F3，无需特殊处理。

分部分进度（展开）按「采购单」把型号分组，没有采购行的型号归为「库存直发」，只在 `GET /items/{id}/parts` 时实时计算，不落库。

备选方案：按采购单、入库、出库、出运单据自己判断阶段。不采用，原因是型号进度已经统一了这几种单据的口径（含部分取消、撤回重算），重复实现容易不一致。

### D4 节点提醒用「节点事件时间」决定是否替换下一步
履约行没有固定节奏，不使用 `Cadence`。推导时算出最近一个已发生节点 `(node_code, node_date)`，节点来源：
- 订单创建日；
- 首张采购单下单日；
- 最近一次实拍完成日；
- 最近一次出运日；
- 首次出现异常的日期。

处理规则：
- `node_date` 晚于跟进对象的 `last_followup_at` 日期，且与当前 `node_code` 不同时，下一步改为该节点的默认动作，日期 = `node_date`，`next_action_source = 1`；
- 否则保留当前下一步（业务员填写的，或已生成的系统提醒）。业务员记跟进后，同一节点不会再次覆盖；
- 业务员的下一步过期后，没有新节点也不清空，按逾期显示（与第一期一致）；
- 没有任何待处理节点时 `next_action_date` 为空，列表显示系统说明文字（由当前阶段和单据算出，如「等供应商发货，预计 11-02」），不进入今日待办。

`followup_item` 新增 `node_code`、`node_date`。默认动作文字放在 `sop_stage.default_actions`，按节点编码取。

### D5 履约异常
`exception_flags` 为位掩码：
- 1-采购超期：口径同 `ShipProgress.OVERDUE`，限定为这张订单的采购行；
- 2-到货差异待处理；
- 4-出库单撤回未重建。

异常说明文字在查询时按位拼出。`exception_since` 记录首次出现的日期，作为节点「出现履约异常」的 `node_date`。全部异常消除时两个字段清空。列表「有异常」筛选用 `exception_flags > 0`。

### D6 预计送达与转入售后
- 估算：对订单所在的每张已出运出运单，`shipped_date + 时效`。`carrier` 去掉空格、转大写后，以快递承运商清单中任一项开头的，按快递天数计算。整单取所有出运单里最晚的日期。
- 字段：
  - `eta_date`：当前预计送达；
  - `eta_source`：1-估算、2-业务员；
  - `eta_first`：首次估算的日期，只写一次，用于按时送达统计；
  - `eta_note`：如「DHL · 按 5 天估算」。
- `eta_source = 2` 后不再被估算覆盖；出运单作废导致没有已出运单时，`eta_*` 清空，回到 F5。
- 转入售后：resync 时 `eta_date <= today`，或订单 `progress_code = COMPLETED`，履约行关闭，售后行生成。日期到达的转入由每晚 02:40 的核对完成，所以当天早上已在 A1；订单详情「客户已收货」走事件实时转入，直接进 A2。
- 系统参数（平台行，可按租户覆盖）：
  - `sales.followup.transit.express-carriers = DHL,FEDEX,UPS,TNT,EMS,ARAMEX`
  - `sales.followup.transit.express-days = 5`
  - `sales.followup.transit.other-days = 10`

### D7 售后评价阶段
售后行在 `followup_item` 上新增：
- `signed_date`：确认收到的日期，取记跟进当天或订单完成日；
- `used_ok`；
- `satisfaction`：1-满意、2-一般、3-不满意；
- `dissatisfied_reason`；
- `review_status`：0-未处理、1-先不邀请、2-已邀请、3-已评价、4-婉拒、5-未评价；
- `review_platform`、`review_invited_at`、`review_remind_count`；
- `after_sales_id`：当前关联的售后单。

推导优先级：
1. 有处理中的售后单 → A3；
2. 有待客户确认的售后单 → A4；
3. `satisfaction` 已填 → A6 或结束；
4. `used_ok`，或签收后的售后单都已结案 / 撤销 → A5；
5. `signed_date` 有值 → A2；
6. 否则 A1。

售后单撤销后，按剩余条件回到 A1 或 A2。A3、A4 行的下一步直接读售后单的 `next_action`、`next_update_at`（`next_action_source = 4`-来自售后单），今日待办排除这类行，避免与售后单自己的时限提醒重复。

售后结束时写 `closed_at`，`close_reason` 新增 5-售后结束。A6 的「两次提醒仍未评价」由核对任务判断：`review_invited_at + 间隔 × (次数 + 1)` 已过且 `review_remind_count` 已达上限时结束。参数：
- `sales.followup.review.remind-days = 7`
- `sales.followup.review.remind-times = 2`

### D8 记一次跟进的阶段字段与事务边界
`RecordFollowupRequest` 和 `customer_followup` 新增：

| 字段 | 用途 |
| --- | --- |
| `notice_mask` | 履约「沟通了什么」位掩码：1-运单照片、2-新交期、4-客户要配合、8-没回复（8 计入 `no_reply_streak`） |
| `eta_date` | 改预计送达，或 A1「还没收到」的新日期 |
| `receipt_answer` | A1：1-已收到无问题、2-有问题、3-还没收到 |
| `use_answer` | A2：1-正常、2-有问题、3-还没开始用 |
| `issue_types` | 少件、破损、型号不对、其他，逗号分隔 |
| `satisfaction`、`dissatisfied_reason` | A5 满意度和原因 |
| `review_status`、`review_platform` | A5、A6 的评价状态和平台 |

服务端校验：
- 字段必须与跟进对象所在阶段匹配，不匹配时报 `FOLLOWUP_STAGE_MISMATCH`；
- A1「还没收到」必须带 `eta_date`，且不早于今天；
- A5 选一般、不满意时必须带原因。

`FollowupService.record` 仍在一个 `@Transactional` 中完成：
1. 写入跟进记录；
2. 更新跟进对象；
3. A1「已收到无问题」时调用 `SalesOrderService.confirmReceivedByFollowup(soId)`，内部复用 `complete` 的校验。条件不满足时返回 false，不抛异常，`RecordResultVO.orderCompleted = false`，前端据此提示。
4. 档案补充同第一期。

「有问题」不在后端建售后单：前端收到保存结果后打开 `CreateAfterSalesModal`，带入 `customerId`、`soId`、`symptom`。售后单保存后由 D2 的同步把阶段推到 A3。这样售后单的校验、权限和附件都沿用原弹窗，跟进事务不跨到售后模块。

### D9 阶段停留时间
`followup_item` 新增 `stage_since`（datetime）。upsert 时 `stage_code` 变化即写当前时间；迁移时现有行取 `create_time`。`sop_stage` 新增 `ref_days`（int），迁移按规格默认值写入，三板块共 19 行。

### D10 团队视图接口 `GET /api/v1/followups/team`
参数：
- `period`：week、last-week、month；
- `deptId`：可选，须在数据权限内；
- 负责人范围沿用 `owners(ownerScope)` 的数据权限逻辑。

返回一次性组装：
- **结果**：7 个指标，每个指标一条按负责人集合与时间范围的聚合 SQL，查询的都是单表加 `customer` 的范围：
  - 新商机：`opportunity.create_time`；
  - 发出报价：首次 `sent_at` 落在区间内的报价单，按版本号最小的那次发送判断；
  - 发出 PI：同报价；
  - 到账成交、复购：`sales_order.create_time`，复购为客户之前已有已完成订单；
  - 按时送达：`signed_date` 在区间内的售后行，比较 `signed_date <= eta_first`；
  - 评价：区间内记录的 `review_status`，取 `customer_followup` 记录。
- **按负责人**：`followup_item` 按 `owner_id, board` 分组计数；逾期、多次延后用条件求和；记跟进次数取 `customer_followup` 中 `record_type = 1` 的记录。
- **阶段**：`GROUP BY board, stage_code` 统计数量和 `AVG(DATEDIFF(today, stage_since))`，再与 `ref_days` 比较。
- **异常**：
  - 5 类条件各查前 20 条，按客户价值排序后合并，返回前 5 条和总数；
  - 「全部查看」调用同一接口，参数 `exceptions=all`，最多返回 100 条。

指标的口径都写在接口注释和规格里，避免与销售统计页不一致：团队视图按跟进负责人统计，销售统计按订单归属统计，两者不要求一致。

### D11 前端
- **`FlowCards`**：`available` 由后端 `overview.boards[].available` 决定，本期 board 3、4 为 true。
- **`StageBar`**：配置来自 `overview.groups`。订单履约末尾为「有异常」；售后评价没有末尾项。
- **`FollowupTable`**：
  - 新增订单履约、售后评价两套列；
  - 订单履约的行可展开（antd Table `expandable`），展开时请求 `/items/{id}/parts`；
  - 售后评价的 A3、A4 行把「记跟进」换成「看售后单」。
- **`RecordFollowupModal`**：中间区块按 `item.board` 和 `stageCode` 切换为 `FulfilQuestions`、`ReceiptQuestions`、`UseQuestions`、`SatisfactionQuestions`、`ReviewQuestions`，其余部分不变。保存结果为「有问题」时打开 `CreateAfterSalesModal`（新增 `defaults` 属性：customerId、soId、symptom）。
- **`TeamView`**：
  - 新组件，页面状态 `view: 'mine' | 'team'`，只有有 `crm:followup:team` 时显示 Segmented；
  - 点数字时设置 `ownerScope` 并切回 `mine`，可带板块、阶段或今日待办参数。
- **售后结案弹窗**：增加「案例沉淀」TextArea。
- **客户详情跟进分区**：数据来自 `customerSummary`，已经按客户汇总所有板块，只需在链路卡片上按板块显示标记。

### D12 SOP 内容导入
迁移 SQL 由 Excel《订单履约 SOP》F1–F6、《售后与评价 SOP》A1–A6 整理成 INSERT，写入 `sop_stage`，`board` 分别为 3、4。内容包括：
- `goal`、`checklist`（取 BAU Checklist）、`gate_text`、`example_dialog`；
- `auto_hint`：系统怎样自动推进的说明，按规格撰写；
- `default_actions`：按 D4 的节点编码和 D7 的阶段写默认下一步。

F7 的清单并入 A1，A7 不导入。

履约异常和售后常见问题的应对办法写入 `sop_blocker`，新增 `board` 列（默认 2，表示现有商机卡点），新码值如下：
- 履约异常：`SUPPLY_DELAY` 采购延期、`CUSTOMS` 清关延误、`DISCREPANCY` 到货差异、`LOGISTICS` 物流异常；
- 售后：`REVIEW_INVITE` 评价邀请。

作战卡按板块读取。

### D13 AI 整理扩展
`followup-extract` 的输入增加当前板块和阶段；输出增加可选字段 `receipt_answer`、`use_answer`、`issue_types[]`、`satisfaction`、`review_status`、`eta_date`，只在对应阶段输出。`FollowupExtractResultHandler` 原样透传，前端只把与当前阶段匹配的字段预填到表单。「AI 写一条」新增两个意图：`logistics_update`（物流更新）、`review_invite`（评价邀请），由 `followup-reply` 按意图切换提示词。本期只写契约测试，不在本机跑真实模型。

## Risks / Trade-offs

- **[预计送达不准]** 估算比实际早时，客户还没收到就被问签收；比实际晚时，签收提醒滞后。→ A1 有「还没收到 + 新日期」，记跟进可以改预计送达；按时送达的统计口径在团队视图注明是按估算日期比较。
- **[事件入口多，遗漏后数据滞后]** 采购、仓库、出库、出运、售后共十多个入口。→ 每晚核对兜底，核对日志记录修正条数；`FollowupHooksTest` 为每个入口写一个「调用后跟进对象阶段正确」的集成用例。
- **[跟进确认收货改了订单状态]** 记跟进会把订单置为已完成，已完成的订单不能再部分取消或改进度。→ 只在所有型号都到最后一步时才做，规则与手动确认完全相同；操作日志注明来自跟进记录。
- **[团队视图聚合慢]** 负责人多、跟进记录多时实时聚合会变慢。→ 所有聚合都按 `tenant_id + owner_id + 时间` 走索引：`customer_followup` 加 `(tenant_id, operator_id, create_time)`，`followup_item` 已有 `idx_owner_todo`。超过 2 秒再考虑每晚快照。
- **[第二期依赖第一期未合并的代码]** 第一期还在 `feat/customer-followup`，没有合入 develop。→ 本期从第一期分支拉 `feat/customer-followup-fulfillment`；第一期合并后改为基于 develop 变基，PR 依次合并。规格中修改的「客户跟进页与板块链路」依赖第一期先归档。

## Migration Plan

迁移 `V1.5.9__customer_followup_fulfillment.sql`：
1. `followup_item`：
   - 删除唯一键 `uk_target`，新建 `uk_board_target (tenant_id, board, target_type, target_id)`；
   - 新增 D4–D9 的字段；
   - `stage_since` 回填为 `create_time`；
   - 加索引 `idx_board_stage (tenant_id, board, stage_code)`。
2. `customer_followup`：新增 D8 的字段，加索引 `idx_operator_time (tenant_id, operator_id, create_time)`。
3. `sop_stage`：新增 `ref_days`，回填 S1–S7 的默认值。
4. `sop_blocker`：新增 `board`，默认 2。
5. 写入 F1–F6、A1–A6 的 `sop_stage` 和新卡点，作为 `V1.5.10__sop_fulfil_aftersales_content.sql` 单独一个文件，便于以后单独修订内容。
6. `after_sales`：新增 `case_note`（varchar 500）。
7. `sys_config`：新增 D6、D7 的 5 个参数。

存量数据不在 SQL 里生成，由启动完成后的核对任务按 D6 和规格的上线规则生成：进行中的订单进履约；预计送达在 30 天内、未完成的已出运订单进 A1。

回退：
- 删除 board 3、4 的 `followup_item` 行；
- 恢复唯一键 `uk_target`；
- 删除新增的列、参数和 F、A 阶段的 SOP 行，`sop_blocker` 删除 board ≠ 2 的行；
- 代码同时回退到第一期。

`after_sales.case_note` 删列前先导出，避免丢失已填写的内容。

PR 说明需写明：迁移版本 V1.5.9、V1.5.10，可回退；不新增 `.env` 配置项。
