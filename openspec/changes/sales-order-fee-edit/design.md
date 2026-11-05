## Context

- `sales_order_fee`（`so_id`、`fee_name`、`fee_type`、`amount`、`amount_cny`、`remark`）已存在，PI 转订单时从 PI 复制；手动建单（`CreateOrderRequest`）没有费用。
- `ReceiptPosition.ofOrder`：PI 转成的订单取 PI 的合计与收款；手动订单取 `o.getTotalAmount()` 与订单自己的收款。所以手动订单改合计后，收款状态、多收直接按新合计算。
- `OrderCostService` 按订单读取合计与收款计算收入、毛利，查询时实时计算。
- 操作日志：订单详情已有操作记录（采购员、进度、附件等修改写日志）。

## Goals / Non-Goals

**Goals:** 漏填的运费能补上，金额、收款、毛利一致；修改可追溯；权限可控。

**Non-Goals:** 改型号、数量、单价、折扣、币种、汇率；改 PI 转成的订单；代收款类型的费用（手动订单只有普通费用）；修改后自动通知客户或重新导出单据。

## Decisions

### D1 只开放手动订单
PI 转成的订单的收款属于 PI、按 PI 合计计算，改订单费用会让订单合计与收款基数不一致，而且客户手里的 PI 不变。保持「取消 → PI 出新版本 → 重新转」，不在这期解决。

### D2 接口 `PUT /api/v1/sales/orders/{id}/fees`
请求 `{fees: [{id?, feeName, amount}], reason, expectedTotal}`，整组替换：带 id 的更新、没带的新增、缺少的软删除。`expectedTotal` 为前端看到的原合计，不一致时拒绝「订单已被修改，请刷新」，防止两人同时改。`@PreAuthorize("@perm.has('sales:order:fee-edit')")`，并校验订单在数据范围内。

### D3 重算（同一事务）
1. 校验：手动订单、状态非已取消 / 已完成、非历史迁移；金额 > 0；≤ 10 行；原因非空。
2. 写费用行：`amount_cny = amount × 订单汇率`，HALF_UP 两位小数。
3. 订单 `fee_amount`、`total_amount = 型号小计 + 费用 − 折扣`、`total_amount_cny = total_amount × 汇率` HALF_UP 两位小数。
4. 按 `ReceiptPosition.ofOrder` 重算并回写订单的收款状态字段（与登记到账后的刷新路径相同）。
5. 写操作日志（每行变化 + 合计变化 + 原因）。

毛利由 `OrderCostService` 实时计算，不需要额外回写。**事务边界**：费用行、订单合计、收款状态、操作日志在同一事务；跨模块只读收款记录，不改收款、应收凭证。若该订单已生成财务凭证且凭证已锁定，拒绝修改并提示「订单已有锁定的凭证，不能修改费用」（遵守不修改已结算凭证的规定）。

### D4 手动建单的费用
`CreateOrderRequest` 增加 `fees`（可选，≤ 10 行，金额 > 0），类型固定普通费用，合计计算同 D3 第 3 步。

### D5 权限迁移
新资源 `sales:order:fee-edit`（挂在销售订单菜单 100079 下）、加入含销售订单菜单的套餐；授给 `ROLE_ADMIN` 内置角色与名称含「总经理」的角色（与 `V1.3.5` 的识别方式一致）。回退：删除 `role_resource` 授权、从套餐移除、删除资源。

## Risks / Trade-offs

- 改合计后已到账的订单可能变「部分到账」或出现多收，这是期望结果，确认弹窗明确提示收款状态变化。
- 名称含「总经理」的角色识别依赖命名；不符合时管理员在角色管理手动授权。
