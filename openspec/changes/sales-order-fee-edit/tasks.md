## 1. 权限与建单

- [x] 1.1 `V1.5.17__sales_order_fee_edit.sql`（design D5），测试库迁移成功，`TenantIsolationContractTest` 通过；回退语句写在迁移注释里（本地回退演练未做）
- [x] 1.2 `CreateOrderRequest.fees` 与合计计算（design D4）。测试：小计 USD 119.62 + 运费 35.00 = 154.62，人民币按汇率 HALF_UP

## 2. 修改费用

- [x] 2.1 `PUT /api/v1/sales/orders/{id}/fees`（design D2、D3）。契约测试 `manualOrderFees_createAndEdit_recalcReceipt_guarded` 覆盖：建单带费用、补费用后部分到账、改少后回到已到账、`expectedTotal` 不一致、没变化、没原因、已取消、操作日志内容（没权限、PI 订单、已完成、迁移订单走同样的校验分支，未单独写用例）
- [x] 2.2 订单毛利按订单合计实时计算，改费用后无需回写（未单独补测试）

## 3. 前端

- [x] 3.1 手动建单表单的费用行
- [x] 3.2 订单详情费用区「修改费用」弹窗（原因、合计变化与收款状态变化提示），PI 订单显示说明
- [x] 3.3 `npm run lint`、`npx antd lint ./src`、`npm test` 通过

## 4. 验证

- [ ] 4.1 本地走查：手动订单补运费、改少出现多收、业务员看不到按钮
