## 1. 原型

- [x] 1.1 OpenPencil 原型 C1、C2（`docs/03-产品原型/03-业务域/22-登记出运选择货代/`），2026-11-19 用户确认

## 2. 后端

- [x] 2.1 `ShipRequest` 增加 `customerForwarder`，`ship` 以选择为准（未传按货代归属），`updateShipping` 不改选择 → 验证：契约测试「没绑定客户也能交客户货代」「绑定客户的也能选我们的货代（运单号必填）」「修改运单不切换」

## 3. 前端

- [x] 3.1 `ShipModal` 按原型增加「交给谁」，切换字段与必填，修改运单只读显示选择，去掉重复提示框 → 验证：Jest 测试切换后必填项与按钮文案变化
- [x] 3.2 `npm run lint`、`npx antd lint ./src`、`npm test` 通过 → 验证：命令输出
