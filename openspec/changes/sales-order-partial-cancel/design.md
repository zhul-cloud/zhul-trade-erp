## Context

动机见 proposal.md。现状：

- 销售订单的成交内容创建后不能改，只能整单取消；整单取消时 `RequirementLifecycle.onOrderCancelled` 关闭没下单的需求、撤出草稿行，已下单的标「订单已取消」。
- 下游大量逻辑直接读 `sales_order_item.quantity`：订单进度、货物状态、采购需求、出库、订单成本（采购订单行数量超过型号数量时按型号数量封顶）。
- 收款记录属于 PI，PI 收款状态由 `PiSummary` 按 PI 合计计算；`ReceiptPosition` 给客户余额的代收、多收、订单收入提供「合计」。
- 闲置库存只由到货差异的「多发 → 暂存」生成，记录 `discrepancy_id`。

## Goals / Non-Goals

**Goals:**
- 部分取消后，下游不需要逐处改：`quantity` 直接表示剩余数量。
- 钱和货都能对上：多收走客户余额的「待处理多收」，仓库里多出来的货进闲置库存。

**Non-Goals:**
- 部分取消的撤回（客户又要回来，按原流程开 PI 或手动建单）。
- 已下单采购订单行的部分取消（采购员仍用现有方式：取消整张采购订单，或保留后到货转闲置库存）。
- 退款申请（挪到 ③ 售后与退货）。
- 给客户的新单据（不出新版 PI）。

## Decisions

### 1. `quantity` 表示剩余数量，另存原数量与已取消数量

型号行加 `original_quantity`（迁移时回填为当前数量）与 `cancelled_quantity`；部分取消时 `quantity -= n`、`cancelled_quantity += n`，金额等按 `quantity` 重算。下游读 `quantity` 的逻辑自动用剩余数量。数量为 0 的行保留，进度、货物状态、「客户已收货」判断跳过它们。

- 备选「保留 quantity 为原数量，下游都改成 quantity − cancelled」：要改的地方太多，容易漏，不采用。

### 2. 部分取消记录单独成表

`sales_order_partial_cancel`：订单、型号行、数量、单价、金额、原因、说明、操作人、处理结果摘要（撤出草稿 N、闲置库存 N、标记采购单），用于订单详情的取消记录与审计。

### 3. 采购需求按「未排单 → 草稿 → 在仓 → 已下单」扣减

在 `RequirementLifecycle` 新增 `onItemReduced(soItem, n)`，复用现有的 `RequirementQty`（草稿、已下单数量）与 `drafts.takeFromDrafts`：
- 未排单数量 = 需求数量 − 草稿 − 已下单；
- 在仓数量 = 合格入库 − 已交货代 − 已出运（按型号行汇总，从已下单数量中扣），生成闲置库存；
- 已下单未到货：需求数量减少，采购订单行记 `so_reduced_quantity`（累计），详情显示「来源订单减少 N 个」并写日志。

验收入库时，若型号行的合格入库数量（含本次）超过订单型号数量，超出部分直接生成闲置库存（来源「订单部分取消」），不计入订单型号的合格数量。

### 4. 应收金额：PI 有有效订单时用订单合计

`PiSummary` 与 `ReceiptPosition` 统一取「应收合计」：PI 有有效订单（已转订单且没取消）时取订单合计，否则取 PI 合计。待处理多收的扫描条件同步改为按应收合计比较。部分取消后对订单与它的 PI 各刷新一次收款状态。

- 备选「部分取消时同时改 PI 合计」：PI 是发给客户的单据快照，不能改，不采用。

### 5. 闲置库存加来源

`stock_hold` 加 `source_type`（1-到货差异、2-订单部分取消）、`so_id`、`so_item_id`，`discrepancy_id` 改为可空。

### 6. 事务边界

部分取消（型号行、订单合计、取消记录、采购需求扣减、草稿撤出、闲置库存、采购单标记、收款状态刷新）在一个本地事务内完成，由 `SalesOrderService` 声明；开始时锁订单行与相关采购需求行（`FOR UPDATE`），防止和下单、入库并发。

### 7. 金额精度

行金额 = 剩余数量 × 单价，本位币 = 金额 × 订单汇率，HALF_UP 两位；百分比折扣 = 新小计 × 百分比 HALF_UP 两位；固定折扣超过新小计时按小计封顶。

## Risks / Trade-offs

- [部分取消后 PI 合计与订单合计不同，客户看到的 PI 还是原金额] → 订单详情与收款面板写明「订单合计（部分取消后）」；多收由财务按待处理多收处理。
- [采购员没处理被标记的采购订单，货照常到] → 到货时超出部分自动进闲置库存，不会算进订单成本（订单成本本来就按型号数量封顶）。
- [历史订单没有原数量] → 迁移回填 `original_quantity = quantity`。

## Migration Plan

1. 迁移（版本取最新序号）：`sales_order_item` 加 `original_quantity`（回填）、`cancelled_quantity`；新建 `sales_order_partial_cancel`；`purchase_order_item` 加 `so_reduced_quantity`；`stock_hold` 加 `source_type`（存量为 1）、`so_id`、`so_item_id`，`discrepancy_id` 改为可空；新增字典「部分取消原因」。
2. 回退：新增字段与表不影响旧版本运行；回退前确认没有部分取消记录（有的话旧版本会把剩余数量当原数量显示），回退 SQL 删除新表与新增字段。
