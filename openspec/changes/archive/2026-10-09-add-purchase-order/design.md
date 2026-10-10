## Context

供应链单据模型（`docs/02-产品PRD/04-供应链域/供应链单据模型-设计-V1.0.md`）的第①期。现状：

- 销售订单型号行有 `purchaser_id`、`cost_price`（采购成本价，人民币、不含税）。
- 报价行 `quotation_item.cost_quote_id` 指向被选为采购成本价的回价 `sourcing_quote`，上面有 `supplier_id`、`shop_name`、`quoted_by`。
- 订单进度由字典 `sales_order_status` 管理，行进度 `sales_order_item.progress_code` 手动推进。
- 编号规则已预留类型代码 `PO`（对外单据，带租户前缀）。
- 后端还没有 `purchase` 模块。

## Goals / Non-Goals

**Goals:**
- 订单型号自动变成采购需求，采购员可以拆分、指派，按供应商合并下单。
- 采购单记录实际采购价，按不含税口径与目标价比较，得出砍价金额与砍价率。
- 订单进度的「待采购 / 已下单」由采购单驱动。
- 订单取消、重转时，已经下给供应商的货不会丢，也不会被重复采购。

**Non-Goals:**
- 不做供应商发货、入库、到货差异、暂存货（第②期）。
- 不做应付、付款、报销、采购谈判报表；付款条件只记录（第③期）。
- 订单毛利仍用报价时的采购成本（第③期换成实际成本）。
- 不做采购单导出、下单审批。

## Decisions

### 1. 数据模型

```
sales_order_item 1 ──N purchase_requirement N ──1 purchase_order_item N ──1 purchase_order
                                                                         ├─ purchase_order_fee
                                                                         ├─ purchase_order_attachment
                                                                         └─ purchase_order_log
```

- **`purchase_requirement`（业务表）**：
  - 来源：`so_id`、`so_item_id`、`quotation_item_id`（用于重转接回）。
  - 型号快照：`model`、`brand`。
  - 数量与价格：`quantity`、`target_price`（DECIMAL(18,2)，可空，人民币不含税）。
  - 负责人与建议：`purchaser_id`、`suggested_supplier_id`、`suggested_shop_name`。
  - 状态：`status`（1-有效、2-已关闭、3-订单已取消）。
  - 已下单数量、草稿中数量不落库，由采购单行实时汇总，避免两处不一致。
  - 需求一行对应采购单至多一行，但一条需求可以分几次进不同采购单（部分下单），所以关系为 N:1。
- **`purchase_order`**：
  - 编号与对象：`po_no`（草稿为空，确认下单时生成）、`supplier_id`、`purchaser_id`。
  - 状态：`status`（1-草稿、2-已下单、3-已取消）、`order_date`。
  - 币种与税：`currency_code`、`exchange_rate`、`tax_included`、`tax_rate`。
  - 金额：`item_amount`、`fee_amount`、`total_amount`、`total_amount_cny`。
  - 砍价汇总：`target_amount`、`bargain_amount`。
  - 合同与取消：`contract_no`、`contract_amount`、`cancel_reason`。
- **`purchase_order_item`**：
  - 来源：`po_id`、`requirement_id`、`so_id`、`so_item_id`。
  - 型号快照：`model`、`brand`。
  - 价格与砍价：`quantity`、`unit_price`（草稿可空）、`net_price_cny`（不含税人民币单价）、`target_price`（快照）、`bargain_amount`。
  - `amount`。
- **付款条件**：`supplier.payment_terms` 与 `purchase_order.payment_terms` 存同一结构的 JSON 数组（`percent`、`trigger` 1-下单后 2-发货前 3-入库后、`days`），由 `PaymentTerms` 统一校验（合计 100%、最多 6 期）与生成显示文字。最多几期、整体读写，不需要单独建表；第③期生成应付时再按期展开。
- **附件**：`purchase_order_attachment`，存储方式与供应商附件相同：私有存储，经鉴权接口访问。
- **操作日志**：`purchase_order_log` 供采购单详情展示（操作、修改前后的值、操作人、时间）；同时写系统操作日志。
- **字段规范**：金额 DECIMAL(18,2)，汇率 DECIMAL(18,6)，比例 DECIMAL(5,2)；全部带 `tenant_id`、`deleted_at` 与审计字段，外键列加索引，不建数据库外键。

### 2. 精度

- `net_price_cny = unit_price × exchange_rate ÷ (1 + tax_rate)`，按 HALF_UP 保留 2 位后入库，用于显示。
- 砍价金额用完整精度的不含税单价计算，`(target_price − net_price) × quantity`，最后 HALF_UP 保留 2 位。
- 整单砍价率 = Σ 砍价金额 ÷ Σ（目标价 × 数量，仅目标价不为空的行），显示时保留两位小数的百分比，不落库。
- 前端只做即时预览，口径与后端一致，保存后以后端返回为准。

### 3. 自动生成草稿与编号

- **自动生成**：订单生成事务的最后一步，`PurchaseDraftService.place(requirements)`：
  - 按 `(purchaser_id, supplier_id)` 分组，锁定该采购员对该供应商、`currency_code = 'CNY'`、`status = 草稿` 的最近一张采购单（`FOR UPDATE`），有则追加行，没有则新建；
  - 需求池「生成采购单」、拆分、改采购员、「改到其他供应商」复用同一方法，规则只有一处。
- **单价可空**：`purchase_order_item.unit_price` 允许为空，确认下单时校验；不预填目标价，避免不改价直接确认导致砍价为 0 的假数据。
- **编号**：`purchase_order.po_no` 草稿为空（唯一索引允许多个 NULL），确认下单时在同一事务内调用编号服务生成，按确认当天流水；删除草稿不占号。
- **店铺匹配供应商**：需求池生成时，用店铺名称精确匹配本租户启用的供应商名称；匹配不到时前端提供「转为供应商」，建好后用新供应商 ID 重新分组。自动生成不做名称匹配，避免同名误配。
- **备选方案**：订单生成时直接生成已下单的采购单。放弃原因：价格要谈判后才知道，自动「已下单」会让订单进度和砍价数据失真。

### 4. 并发与占用

- 加行、改数量、确认下单时，对涉及的需求加行锁（`SELECT … FOR UPDATE`），在同一事务内校验「数量 ≤ 可下单数量」。
- 草稿也占用数量，防止两位采购员把同一需求重复下单。
- 新建采购单用幂等键防止重复提交。

### 5. 订单进度联动

- 只自动处理「待采购 ↔ 已下单」：
  - 行进度为两者之一时，`Σ 已下单采购单上的数量 ≥ 行数量` → `ORDERED`，否则 → `PENDING_PURCHASE`；
  - 已经手动推进到后面步骤的行不回退。
- 订单级 `progress_code` 沿用现有重算逻辑。
- 手动推进接口拒绝目标为这两步；有需求、还没全部下单的行不能推进到「已下单」之后。
- 存量订单中进度已在「待采购」之后的行没有需求，不受限制。

### 6. 采购员

- 订单行 `purchaser_id` 保留，表示「生成时的采购员」，并作为需求的初始值。
- 详情、列表显示与筛选改为取该行全部有效需求的采购员（`exists` 子查询），不 JOIN 超过 3 张表。
- 订单详情「改采购员」改写该行还有可下单数量的需求，同时更新 `purchaser_id`，保持旧数据的显示一致。

### 7. 取消与重转

- **取消订单**：
  - 没有下单数量的需求置为已关闭；
  - 草稿采购单上的行删除（软删除），草稿整单空了也删除；
  - 已下单数量所在的需求置为「订单已取消」。
- **重新转订单**：
  - 生成新需求后，按 `quotation_item_id` 找同一 PI 下「订单已取消」的需求，把它们的采购单行改挂到新需求、新订单上（更新 `requirement_id`、`so_id`、`so_item_id`），直到新需求数量用完；
  - 原需求的数量相应减少，减到 0 时置为已关闭；
  - 全部在转订单的事务内完成，最后重算新订单行进度。
- **备选方案**：取消订单时直接拦截已下单的情况。放弃原因：客户追加型号是「取消重转」的主要场景，此时货已经下单，拦截会让业务员走不下去。

### 8. 权限

- **菜单**：采购需求 `/purchase/requirements`、采购单 `/purchase/orders`，挂在「采购管理」下，位于历史询价之后、供应商之前。
- **按钮**：
  - `purchase:requirement:split` 拆分采购需求；
  - `purchase:requirement:assign` 指派采购员；
  - `purchase:order:create` 新建、编辑、确认采购单；
  - `purchase:order:cancel` 取消、删除采购单。
- **数据权限**：
  - 需求按 `purchaser_id`、采购单按采购员，复用 `DataScopeResolver`；
  - 没有采购员的需求只对「全部」与管理员可见；
  - 兼职采购角色不授予这两个菜单。
- **迁移授权**：给现有「采购」相关角色授予菜单与按钮，给管理员角色授予全部。

### 9. 事务边界

| 动作 | 同一事务 |
|---|---|
| 生成订单（PI 转成 / 手动） | 订单、订单行、采购需求、（重转时）接回已下单数量与行进度、自动排入草稿采购单 |
| 新建采购单 | 采购单、行、需求加锁与校验 |
| 确认下单 | 分配编号、采购单状态、下单日期、订单行进度、订单进度 |
| 修改已下单的采购单 | 行、合计、砍价、日志、订单行进度 |
| 取消采购单 | 状态、原因、日志、订单行进度 |
| 取消订单 | 订单状态、PI 解锁、需求关闭或标记、草稿行删除 |

### 10. 迁移（Flyway）

- 新建上述表。
- `supplier.payment_terms` 默认为空串。
- 回填需求：「有效」订单中 `progress_code = PENDING_PURCHASE` 的行各生成一条需求：
  - 目标价取 `cost_price`；
  - 采购员取 `purchaser_id`；
  - 建议供应商取 `quotation_item.cost_quote_id → sourcing_quote.supplier_id / shop_name`。
- 菜单、按钮资源与角色授权。
- 编号类型 `PO` 已在编号规则中，无需迁移。
- 存量订单回填的需求不自动生成草稿，留在需求池由采购员一键生成，避免上线时突然冒出一批草稿。

## Risks / Trade-offs

- **已下单的采购单可以随意改** → 本期没有发货、入库，修改全部写日志；第②期会在有发货后限制修改。
- **需求数量不落库，列表需要聚合** → 按需求 ID 批量汇总采购单行（单条 `GROUP BY`），列表分页后再组装；数据量在万级以内足够。
- **重转按报价行匹配**：PI 新版本删掉了某个型号，被删型号的已下单货不会被接回，仍标「订单已取消」，由采购员处理。这是期望的行为。
- **含税判断在单头**：同一张采购单不能混合含税、不含税行。供应商一张单通常统一口径；确有混合时，可以拆成两张采购单。
