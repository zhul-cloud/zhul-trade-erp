## 1. 应收管理

- [x] 1.1 迁移：菜单「应收管理」（100132），授给有收款管理的角色与套餐
- [x] 1.2 后端 `ReceivableService` / `ReceivableController`：有效订单未收、PI 来源取 PI 收款、到期分桶、数据权限
- [x] 1.3 前端应收管理页：汇总卡片点击筛选、按客户 / 按订单、筛选；按客户汇总与筛选的单元测试
- [x] 1.4 契约测试 `ReceivableContractTest`

## 2. 无货分析

- [x] 2.1 后端 `NoStockStatsService` 与接口 `/sourcing-stats/no-stock`：按周 / 按月、最后一次回价、品牌与一级品类、近 8 期走势
- [x] 2.2 询价记录按一级品类筛选；前端可从链接带入筛选
- [x] 2.3 前端「无货分析」页签，点无货数跳询价记录
- [x] 2.4 契约测试 `NoStockStatsContractTest`

## 3. 支付宝、微信付款与收款码

- [x] 3.1 迁移：`tenant_bank_account`、`fin_payment` 加 `pay_channel`；附件类型 6、7 为收款码
- [x] 3.2 收款账户对私账户可设付款渠道（前后端）
- [x] 3.3 供应商收款码：上传、更换、删除接口；编辑供应商不删收款码；详情页结算信息里显示
- [x] 3.4 登记付款：私对私选付款渠道、显示对方收款码与预览、我方账户按渠道过滤；付款单列表与详情显示渠道
- [x] 3.5 契约测试：`ReimbursementContractTest.privatePay_alipayChannel_supplierQr`

## 4. 验收

- [x] 4.1 生产验收：应收管理金额与订单详情一致；无货分析数字与询价记录筛选一致；支付宝付款与收款码全流程
