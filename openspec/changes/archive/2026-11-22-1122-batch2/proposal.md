## Why

用户 2026-11-22 反馈的第二批（原型 `docs/03-产品原型/03-业务域/31-11-22反馈/` V1–V6 已确认）：
- 有应付管理，但没有应收管理，看不出还有哪些客户的款没收、一共多少；
- 想按周、按月看无货的品牌、品类；
- 私对私付款常用支付宝、微信扫码，登记付款时没有地方看对方的收款码，供应商资料也不能存收款码。

## What Changes

- 新增「财务管理 → 应收管理」，只读，排在收款管理之前：
  - 只算有效销售订单，未收 = 订单合计 − 已到账 − 手续费差额，与订单详情同口径；
  - 顶部四项：应收未收、已逾期、7 天内到期、本月到期，后三项点击即筛出；
  - 可按客户（展开看每张订单）或按订单查看，逾期在前；
  - 可按客户、订单号、业务员、币种、到期筛选。
- 询价统计加第三个页签「无货分析」：
  - 按周或按月看询价型号数、无货数、无货率、涉及品牌，较上期有对比；
  - 品牌和一级品类按无货数排行，品牌附主要无货原因；
  - 显示近 8 期的无货率走势；
  - 点无货数跳到询价记录，自动带上「无货 + 品牌或品类 + 这一期日期」筛选。
  - 同一型号在一期里询过多次，按最后一次回价算。
- 询价记录支持从链接带筛选，新增按一级品类（含细分）筛选。
- 私对私付款加「付款渠道」：银行卡、支付宝、微信。
  - 选支付宝或微信时，显示供应商的收款码，可放大扫码；没有收款码时提示去供应商资料上传。
  - 我方付款账户只列同一渠道的对私账户。
- 收款账户的对私账户可设付款渠道（银行卡、支付宝、微信）。支付宝、微信账户只填账户名称和账号（手机号或邮箱）。
- 供应商详情的结算信息里可上传、更换、删除支付宝、微信收款码，每种只保留一张。收款码不出现在「附件」里，编辑供应商时也不会动它。

## Capabilities

### New Capabilities

- `finance/accounts-receivable`：应收管理。

### Modified Capabilities

- `inquiry/sourcing-stats`：增加无货分析。
- `inquiry/price-ledger`：增加按一级品类筛选，可从链接带入筛选。
- `finance/supplier-payment`：私对私增加付款渠道与对方收款码。
- `system/bank-account`：对私账户可设付款渠道。
- `master-data/supplier-settlement-account`：增加供应商收款码。
- `system/navigation`：财务管理下增加应收管理。

## Impact

- 迁移 `V1.5.60__receivables_pay_channel.sql`，可以回退，回退语句写在文件末尾：
  - 新增菜单「应收管理」（100132），授给有收款管理的角色与套餐；
  - `tenant_bank_account`、`fin_payment` 各加 `pay_channel`，默认 1（银行卡），已有数据不变；
  - 只改 `supplier_attachment.category` 的注释。
- 后端：新增 `ReceivableService` / `ReceivableController`、`NoStockStatsService`；改动 `PriceHistoryServiceImpl`、`PaymentService`、`BankAccountServiceImpl`、`SupplierAttachmentSync`、`SupplierServiceImpl`。
- 前端：新增应收管理页、无货分析面板、登记付款收款码面板、供应商收款码；改动收款账户表单、付款单列表与详情。
- 没有新增配置项。
