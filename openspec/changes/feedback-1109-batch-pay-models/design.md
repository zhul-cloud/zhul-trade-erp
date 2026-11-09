## Context

四项反馈互不依赖，都是在现有能力上扩展；原型见 `docs/03-产品原型/03-业务域/11-09反馈二-无货附件批量付款型号分页/`（用户 2026-11-09 已确认）。

## Decisions

### D1 无货附件沿用回价附件

无货回价本来就写一条 `sourcing_quote`（no_stock=1），附件业务类型仍为 `SOURCING_QUOTE`，挂到这条记录上；上限与有货报价相同（5 个）。不加表、不加字段。

### D2 批量付款按「收款方 + 币种」分组，每组复用单笔登记

- 前端按勾选的应付自动分组；请求为 `{payDate, method, idempotencyKey, groups:[{bankAccountId, payeeAccountId, amount, amountCny, attachmentIds, payableIds}]}`。
- 后端 `PaymentService.batchRegister` 在一个事务内对每组组装 `RegisterPaymentRequest` 调用现有 `register`：同收款方同币种校验、外币实付人民币、加锁、幂等、日志全部沿用。每组的核销分配：按到期日（无到期日排后）、再按编号从早到晚依次用付款金额核销，不超过各应付未付；付款金额不能超过该组未付合计。
- 每组幂等键为 `批次键-组序号`；任一组失败整批回滚。一次最多 50 张应付。
- 我方付款账户按币种分开（系统里付款账户本来就区分币种），所以每组单独选，默认该币种第一个账户。批量付款只支持公司对公与私对私，不处理欠款抵扣和供应商优惠（需要时仍走单笔登记）。
- 金额精度：付款金额、核销金额 2 位小数；实付人民币 2 位，汇率按现有规则 HALF_UP 保留 6 位。

### D3 型号弹窗服务端分页

- 销售订单新增 `GET /api/v1/sales/orders/{id}/models?page&pageSize`（每页最多 100），按明细行顺序分页返回品牌、型号、品类、数量、单位、单价、币种；数据范围与订单详情一致。
- 询盘新增 `GET /api/v1/inquiry/customer-inquiries/{id}/models/page?page&pageSize`：已确认的按型号明细分页查询，待确认的按 AI 解析结果分页；原 `models` 接口不变，复制全部型号时调用。销售订单复制全部型号按每页 100 个依次取完。
- 前端抽出通用 `ModelsModal`（列：#、品牌、型号、品类、数量、单价可选），每页 20 个。
