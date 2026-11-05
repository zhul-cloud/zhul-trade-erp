## 1. 数据与采购订单

- [x] 1.1 `V1.5.16__purchase_order_fee_type.sql`（design Migration Plan），测试库迁移成功；回退语句写在迁移注释里（本地回退演练未做）
- [x] 1.2 `PurchaseOrderFeeDO`、保存请求、详情 VO 增加 `feeType`；`PurchaseDrafts` 合计按净额计算；优惠上限校验（design D2）。测试：费用、优惠、超过上限、砍价金额不变、开票成本不变
- [x] 1.3 已下单修改优惠写操作日志并触发应付同步。测试：添加优惠后应付 ¥1,380.00；已付清后改小优惠回到部分付款

## 2. 付款时登记优惠

- [x] 2.1 `PaymentService.register` 支持 `discount`（design D3），同一事务。契约测试：垫付 ¥1,380.00 + 优惠 → 已付清、采购订单有优惠行；付款等于未付时传优惠被拒绝；多张应付核销时传优惠被拒绝；并发时重新校验（沿用现有应付单加锁）
- [x] 2.2 付款面板登记弹窗：付款金额小于未付时显示「剩下的 ¥X 是供应商优惠」与名称输入

## 3. 订单成本

- [x] 3.1 订单成本「采购费用」按净额分摊（design D4，不改代码，`fee_amount` 已是净额）；`OrderCostCalcTest.poDiscountReducesCostByLineShare` 覆盖 F、G 场景，`OrderCostContractTest` 回归通过

## 4. 前端与验证

- [x] 4.1 采购订单编辑与详情的费用行增加类型选择，优惠显示为负数
- [x] 4.2 `npm run lint`、`npx antd lint ./src` 通过；后端相关测试通过（全量由 CI 跑）
- [ ] 4.3 本地走查：线上店铺采购订单 ¥1,440.00 付 ¥1,380.00 登记优惠，检查应付、付款单、订单成本
