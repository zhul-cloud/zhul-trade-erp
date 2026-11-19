# Tasks

## 1. 原型

- [x] 1.1 OpenPencil 原型 C1–C7（已确认）

## 2. 后端

- [x] 2.1 迁移 V1.5.50：备注修改人与时间、采购单行 `remark_alert`，写回退
- [x] 2.2 `ItemRemarks`：按询盘型号 / 订单型号读取，写询盘型号与手动订单型号，已下单采购单行标红并写日志
- [x] 2.3 接口：询盘型号写备注、订单型号写备注、采购单「我已核对」
- [x] 2.4 询盘、我的询价、询价分配、报价单、PI、销售订单、采购需求、采购单返回 `itemRemark`
- [x] 2.5 契约测试 `ItemRemarkContractTest`

## 3. 前端

- [x] 3.1 公用组件 `ItemRemarkText`、`ItemRemarkLine` 与组件测试
- [x] 3.2 询盘详情、销售订单详情可编辑；询价分配、我的询价、报价单、PI、采购需求只读
- [x] 3.3 采购单行标红、顶部提示与「我已核对」

## 4. 验收

- [ ] 4.1 本机页面实测（BrowserOS neo），验收表 `acceptance.md`
