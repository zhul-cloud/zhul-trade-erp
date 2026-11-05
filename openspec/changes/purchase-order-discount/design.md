## Context

- `purchase_order_fee`（`po_id`、`fee_name`、`amount`）；`PurchaseDrafts` 重算合计：`fee_amount = Σ amount`，`total_amount = 货款 + fee_amount + 开票成本`。
- `PayableSyncService`：应付金额 = 采购订单合计 − 已处理的到货差异折价，采购订单保存、修改时同事务重算。
- `OrderCostService`：采购费用（`poFee`）按采购订单 `fee_amount` 按行货款比例分摊。
- `PaymentService`：登记付款、核销、更新应付与付款计划，同一事务，同一应付单串行。

## Goals / Non-Goals

**Goals:** 应付和订单成本按实际付的钱算；采购员在付款面板一步完成。

**Non-Goals:** 按行的优惠（只在整单层面记）；优惠影响开票金额或税点；财务「应付管理」多单核销时登记优惠。

## Decisions

### D1 费用行加类型，金额仍存正数
`purchase_order_fee` 加 `fee_type tinyint(2) NOT NULL DEFAULT 1 COMMENT '类型（1-费用、2-优惠）'`。存正数，靠类型决定加减，避免负数金额在导出、校验、界面上到处特判。`fee_amount` 改为「费用 − 优惠」的净额，于是 `total_amount`、应付同步、订单成本的 `poFee` 公式都不用改。

被否决的方案：在付款单或应付单上加「减免」字段。一笔付款可核销多张应付，减免归属不清；应付金额和订单成本还要各自减一次，口径容易不一致。

### D2 校验
保存采购订单时：优惠合计 ≤ 货款 + 费用合计，否则 `BizException("PO_DISCOUNT_EXCEEDS", "优惠不能超过货款加费用")`。开票成本仍按货款（或开票金额）计算，不扣优惠。砍价计算只用货款行，不受影响。

### D3 付款时登记优惠
`RegisterPaymentRequest` 增加可选 `discount: {name}`，仅当请求来自单张采购订单付款面板（核销明细只有一张、来源为采购订单的应付单）时接受。`PaymentService.register` 的同一事务内顺序：
1. 锁定应付单（沿用现有串行化），计算 X = 未付 − 本次核销到这张应付单的金额，X ≤ 0 时拒绝；
2. 调采购订单服务加一行优惠（金额 X），写操作日志，重算合计 → `PayableSyncService` 重算应付与付款计划；
3. 继续原有登记、核销流程，应付单变为已付清。

**事务边界**：采购订单优惠行、采购订单合计、应付单与付款计划、付款单与核销明细在同一个事务里；任一步失败全部回滚。

### D4 订单成本
`fee_amount` 已是净额，分摊不变。成本明细展开「采购费用」时，按采购订单的费用行列出，优惠行标「采购优惠」并显示负数。

### 精度
原币金额两位小数；人民币 = 原币 × 采购订单汇率，HALF_UP 两位小数；按行分摊时尾差放到金额最大的行（沿用现有规则）。

## Migration Plan

`V1.5.16__purchase_order_fee_type.sql`：加列 `fee_type`，默认 1，已有数据都是费用。回退：`ALTER TABLE purchase_order_fee DROP COLUMN fee_type;`——回退前必须确认没有 `fee_type = 2` 的行，否则这些优惠会被当成费用，合计变大。

## Risks / Trade-offs

- 已付清后再改优惠会使应付回到部分付款或产生供应商欠款，这是现有「应付金额同步」规则，符合预期。
- 线上店铺采购订单若由系统自动草稿生成，优惠只能在采购员手动添加或付款时登记，不自动识别平台优惠。
