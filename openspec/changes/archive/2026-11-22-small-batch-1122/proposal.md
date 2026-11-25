## Why

用户 2026-11-22 提的一批小改：结汇时银行会扣一笔手续费，结汇单记不下；总经理工作台的客户排行只有客户名称，看不出是哪国、找谁；询价记录不能单独看无货的型号。

## What Changes

- 结汇单追加「手续费」（外币，银行从结汇金额里扣）：汇率 = 入账人民币 ÷ (结汇金额 − 手续费)；手续费折人民币按同一汇率，按每笔收款分摊到的结汇金额比例分到收款，计入对应订单的收款手续费（成本）。
- 老板看板「客户 · 本月成交额」的客户列按列表的客户列显示：客户名称、国家 / 地区、主要联系人。
- 询价记录增加「回价」筛选：有价 / 无货。

不在本次：应收管理、无货分析（按周 / 月看无货的品牌与品类）、支付宝 / 微信付款二维码，另出原型确认后再做。询盘 AI 解析品牌没有按别名统一的问题按缺陷登记在整改台账。

## Capabilities

### Modified Capabilities

- `finance/fx-settlement`：登记结汇单增加手续费；分摊与到账人民币含手续费口径。
- `inquiry/price-ledger`：查询询价记录增加有价 / 无货筛选。
- `system/workbench`：老板看板排行的客户显示国家 / 地区与主联系人。

## Impact

- 迁移 `V1.5.59__fx_settlement_fee.sql`：`fin_fx_settlement` 加 `fee_amount`、`fee_amount_cny`，`fin_fx_settlement_allocation` 加 `fee_amount_cny`，默认 0，已有数据不变，可回退。
- 后端：`FxSettlementService`、`OrderCostService` / `OrderCostCalc`、`PriceHistoryServiceImpl`、`BossWidgets`、`SalesStatsService`。
- 前端：结汇单登记 / 列表 / 详情、询价记录筛选、工作台表格组件。
