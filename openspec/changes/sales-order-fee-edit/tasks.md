## 1. 权限与建单

- [ ] 1.1 `V1.5.17__sales_order_fee_edit.sql`（design D5），本地迁移、回退、再迁移；`TenantIsolationContractTest` 通过
- [ ] 1.2 `CreateOrderRequest.fees` 与合计计算（design D4）。测试：小计 USD 119.62 + 运费 35.00 = 154.62，人民币按汇率 HALF_UP

## 2. 修改费用

- [ ] 2.1 `PUT /api/v1/sales/orders/{id}/fees`（design D2、D3）。契约测试：补运费后部分到账、改少后多收、没权限 403、PI 转成的订单拒绝、已取消 / 已完成 / 迁移订单拒绝、没原因拒绝、`expectedTotal` 不一致拒绝、操作日志内容
- [ ] 2.2 订单毛利按新合计计算（`OrderCostService` 测试补一个改费用后的场景）

## 3. 前端

- [ ] 3.1 手动建单表单的费用行
- [ ] 3.2 订单详情费用区「修改费用」弹窗（原因、合计变化与收款状态变化提示），PI 订单显示说明
- [ ] 3.3 `npm run lint`、`npx antd lint ./src`、`npm test` 通过

## 4. 验证

- [ ] 4.1 本地走查：手动订单补运费、改少出现多收、业务员看不到按钮
