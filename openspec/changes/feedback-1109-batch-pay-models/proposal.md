## Why

2026-11-09 线上反馈：询价任务选「无货」时不能上传附件，代理商回复、停产通知等凭证没地方放；应付只能一张一张登记付款，月底集中付款很费事；销售订单的「型号 · 数量」列多型号时看不到全部型号，询盘的「全部型号」弹窗一次加载全部型号，型号多时很慢。

## What Changes

- 询价任务：型号选「无货」时也可以上传附件（与有货报价相同，最多 5 个），提交后在该型号的回价行显示附件数量。
- 应付管理：列表可勾选未付清的应付（一次最多 50 张），点「批量付款」后按「收款方 + 币种」分组，每组生成一笔付款。付款日期、付款方式整批共用；每组单独选我方付款账户（按币种默认）、对方收款账户，填付款金额、外币的实付人民币和付款截图。付款金额默认等于该组未付合计，改小时按到期日从早到晚核销。整批在一个事务里，任何一笔失败整批不保存。
- 销售订单列表「型号 · 数量」列有多个型号时显示「查看」，弹窗分页列出全部型号（每页 20 个）；询盘列表的「全部型号」弹窗改为同样的分页。「复制全部型号」仍复制全部型号。
- 无数据库迁移，无新增配置项。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `inquiry/sourcing-quote`：无货回价可带附件。
- `finance/supplier-payment`：新增批量付款。
- `sales/sales-order`：订单列表可查看全部型号（分页）。
- `inquiry/inquiry-intake`：询盘列表「全部型号」弹窗分页。

## Impact

- 后端：`ItemQuotesRequest` 增加无货附件；`MyTaskServiceImpl.saveQuotes / writeQuotes` 无货也挂附件。`PaymentController` 新增 `POST /finance/payments/batch`，`PaymentService.batchRegister` 逐组复用单笔登记逻辑。销售订单新增 `GET /sales/orders/{id}/models?page&page_size`；询盘新增 `GET /inquiry/customer-inquiries/{id}/models/page`（原 `models` 接口不变，供复制全部型号使用）。
- 前端：`inquiry/my-tasks/index.tsx`；`finance/payables/index.tsx`、`payables/service.ts` 及新的批量付款弹窗；`sales/orders/index.tsx`；`inquiry/customer-inquiry/ModelCells.tsx` 改成可复用的分页型号弹窗。
