# Tasks

## 1. 原型

- [x] 1.1 OpenPencil 原型 B1～B4（`docs/03-产品原型/03-业务域/16-11-18反馈小改动/`），用户已确认（2026-11-18）

## 2. 再下一单

- [x] 2.1 后端 `GET /api/v1/sales/orders/{id}/reorder`：按原订单返回客户、币种、型号（商品档案可选时带档案，否则标空）、数量、单价、成本、采购员、现货 / 期货与普通费用；已全部取消的行不带；权限同手动创建订单。验证：`ReorderContractTest`
- [x] 2.2 前端：列表操作列「再下一单」，手动建单抽屉按原订单预填，未建档型号提示并拦住创建。验证：`reorder.test.tsx`

## 3. 规格参数一行显示

- [x] 3.1 规格参数查看表格各格单行省略、悬停看全文。验证：前端 lint 与类型检查通过，按原型核对

## 4. 档案补全按业务员筛选

- [x] 4.1 后端队列接口加 `ownerId`；前端列表顶部「业务员」筛选，只对全部 / 自定义数据范围显示。验证：`ProfileCompletionContractTest.queue_filtersByOwner`

## 5. 检查

- [x] 5.1 前端 lint、tsc、antd lint、相关 Jest 通过
- [ ] 5.2 后端相关测试在 CI 通过（本机 Docker 未运行）
