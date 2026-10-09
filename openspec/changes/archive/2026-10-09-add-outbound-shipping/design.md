## Context

- 第②期已有发货单（SD）、入库单（GR）、到货差异、订单货物状态；订单进度「待采购 / 已下单 / 已入库」由 `OrderPurchaseProgress` 自动推进。
- 单据模版已支持报价单、PI 的 Excel 渲染与 PDF 导出；CI、PL 只有模版管理。
- 编号类型已预留 SH、CI（对外）、PL（对外），CI 与 PL 共用主体。
- 订单行有 HS 编码、原产地、英文描述；订单有买方、收货人、费用行。

## Goals / Non-Goals

**Goals:**
- 发货通知到出运全程在系统里，订单进度「已交货代」「已出运」自动推进。
- CI、PL 一键生成，箱规来自仓库的装箱记录。
- 国内、国际运费落到订单型号行，为实际毛利打基础。

**Non-Goals:**
- 不做付款、报销、应付（第③期）。
- 不接快递与货代接口。
- 不做合箱、多仓库。

## Decisions

### 1. 数据模型

```
sales_order_item ─1:N─ outbound_order_item ─N:1─ outbound_order (OB) ─N:1─ courier_waybill（国内快递，可多张共用）
                                │                       │
                         outbound_box_item ─N:1─ outbound_box
                                                        └─N:1─ logistics_shipment (SH) ─1:N─ shipment_doc_group (CI/PL)
freight_allocation（来源：国内快递 / 出运单 → 箱 → 订单型号行）
forwarder_statement ─1:N─ forwarder_statement_line ─1:1─ logistics_shipment
```

- **`outbound_order`**：`ob_no`、`so_id`、`forwarder_id`、`source`（1-发货通知、2-直发货代）、`status`（1-待打包、2-已打包、3-已交货代、4-已撤回）、`courier_waybill_id`、`shipment_id`、`supplier_shipment_id`（直发）、`note`、撤回原因。
- **`outbound_order_item`**：`so_item_id`、型号快照、`quantity`、`unit_price`（订单单价，用于按货值分摊）。
- **`outbound_box`**：`outbound_id`、`box_no`、`length`、`width`、`height`（厘米）、`gross_weight`、`net_weight`（DECIMAL(10,2)，千克）。
- **`outbound_box_item`**：`box_id`、`outbound_item_id`、`quantity`。
- **`courier_waybill`**：`carrier`、`tracking_no`、`sent_date`、`freight`（CNY）、`payer_id`、`forwarder_id`、`status`（1-有效、2-已撤销）。
- **`logistics_shipment`**：`sh_no`、`customer_id`、`forwarder_id`、`status`（1-待出运、2-已出运、3-已作废）、`carrier`、`waybill_no`、`shipped_date`、`freight`（CNY）、`reconciled`、`owner_id`（业务员，数据权限）。
- **`shipment_doc_group`**：`shipment_id`、`so_ids`（合成一组时多张）、`ci_no`、`pl_no`、`payment_ref`、`status`。
- **`freight_allocation`**：`source_type`（1-国内快递、2-国际运费）、`source_id`、`box_id`、`so_item_id`、`amount`、`deleted_at`（重新分摊时作废旧行）。
- **`forwarder_statement`** 与 **`forwarder_statement_line`**：货代、月份、状态（1-草稿、2-已确认），行上出运单、我们登记的运费、对账金额、说明。
- **`supplier.volume_divisor`**：INT，默认 5000；**`supplier_shipment.direct_forwarder_id`**：直发货代。
- 字段规范同前：`tenant_id`、审计字段、`deleted_at`，金额 DECIMAL(18,2)，外键列加索引。

### 2. 数量口径（不落库，按出库单与出运单汇总）
- 已出库（占用）数量 = 未撤回出库单上的数量；可出库 = 合格入库 − 已出库。
- 已交货代数量 = 状态为「已交货代」的出库单上的数量；已出运数量 = 已出运出运单里出库单上的数量。
- 货物状态：在仓 = 合格入库 − 已交货代 − 已出运（待打包、已打包的出库单还算在仓）；已交货代 = 已交货代且还没出运的出库单数量；已出运 = 已出运出运单里的数量。

### 3. 订单进度同步
- `OrderPurchaseProgress.sync` 扩展为 `OrderProgressSync`：先按出运、交货代判断（已出运数 ≥ 型号数 → SHIPPED；已交货代 + 已出运数 ≥ 型号数 → TO_FORWARDER），否则走原有采购与入库规则。自动范围：当前进度在五个自动步骤之一。
- 系统外采购的型号也参与出库与出运的推进（没有采购需求时跳过采购与入库那部分，回退时保持原进度）。

### 4. 直发货代
- 发货单 `direct_forwarder_id` 不为空时，仓库「待收货」排除它；出运单确认时调用入库服务生成 GR（`skipShoot=true`，实收 = 合格 = 确认数），再生成 `source=2` 的出库单（状态已交货代，箱子按确认时登记）。

### 5. CI / PL 生成
- 复用 `document` 模块的 xlsx 渲染：表头字段 + 明细行复制 + 费用行；PL 新增「箱内首行写箱字段、按箱合并单元格」：渲染明细时记录每箱起止行，合并 `${box.*}` 所在列。
- PDF 转换沿用报价单的方式。
- 编号：生成单证组时分配 CI 号，PL 用同一「年月日 + 流水」主体换类型代码；重新生成不换号。

### 6. 运费分摊
- `FreightAllocator`：输入（运费、若干箱：计费重、箱内行货值），输出每行金额；体积重保留完整精度，最终 HALF_UP 两位；尾差规则见规格。
- 国内快递多张出库单共用：先按出库单计费重之和分到出库单，再在出库单内按箱分。
- 外币订单的货值按订单汇率折人民币后比较。

### 7. 菜单、按钮与授权
- 仓库管理 → 出库打包 `/warehouse/outbounds`，按钮 `warehouse:outbound:pack`（打包、交快递、撤销交货代）；授予 ROLE_WH 与租户管理员。
- 单证物流（新分组）→ 出运单 `/logistics/shipments`（按钮 `logistics:shipment:edit`），货代对账 `/logistics/statements`（按钮 `logistics:statement:edit`）。
- 订单详情按钮 `sales:order:ship-notice`（发货通知）；授予有「销售订单」菜单的角色与租户管理员；出运单菜单同样授予这些角色；货代对账只授予租户管理员。

### 8. 事务边界

| 动作 | 同一事务 |
|---|---|
| 发货通知 | 出库单、行、订单日志 |
| 交国内快递 | 快递单、出库单状态、国内运费分摊、订单进度 |
| 撤销交货代 | 快递单（全部撤销时作废）、出库单状态、分摊作废与重算、订单进度 |
| 直发确认 | 入库单与差异、直发出库单与箱、订单进度 |
| 登记出运 | 出运单、国际运费分摊、订单进度 |
| 对账确认 | 对账单、出运单运费与对账标记、重新分摊 |

## Risks / Trade-offs

- 分摊结果依赖订单单价，订单是外币时按订单汇率折算；以后做实际毛利时口径保持一致。
- PL 合并单元格依赖模版明细行的列位置，模版调整后需要按示例重新上传。
- 直发货代没有我们的验货与拍摄；有质量问题时只能在客户反馈后处理。
