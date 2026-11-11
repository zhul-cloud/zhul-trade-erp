## Why

2026-11-09 线上反馈：按 PI 转订单时没法当场写备注、传附件，要建好后再进详情补；业务员写在销售订单上的采购注意事项，采购员在采购需求和采购订单里看不到；财务登记结汇时银行回单上写的是汇率，现在要先自己乘出人民币再填。

## What Changes

- 「转成订单」弹窗（PI 详情与「新建销售订单 → 按 PI 创建」共用）增加附件与备注：备注默认带出 PI 当前版本的备注，可以修改；附件规则与手动建单相同。
- 销售订单的备注实时显示给采购：采购需求「按订单」视图在订单行下显示备注；采购订单详情按来源销售订单逐条列出备注。只在系统内显示，采购订单本来就没有导出，不发给供应商。
- 登记结汇单先填结汇汇率（6 位小数），实际入账人民币按「结汇金额 × 汇率」四舍五入（HALF_UP）到 2 位自动算出，允许手动微调到银行实际入账金额；保存仍按实际入账人民币 ÷ 结汇金额反算并保存 6 位汇率，规则不变。
- 无数据库迁移，无新增配置项。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `sales/sales-order`：「由 PI 转成销售订单」转订单时可以填写备注与上传附件。
- `purchase/purchase-requirement`：「采购需求列表」按订单视图显示销售订单备注。
- `purchase/purchase-order`：「采购订单列表与详情」详情显示来源销售订单的备注。
- `finance/fx-settlement`：「登记结汇单」增加结汇汇率输入，自动算出实际入账人民币。

## Impact

- 后端：`ConvertOrderRequest` 增加 `remark`、`attachmentIds`，`SalesOrderServiceImpl.convert()` 写入备注、挂附件；采购需求按订单视图的 VO 与采购订单详情 VO 增加销售订单备注（按 `soId` 批量查询 `sales_order.remark`）。
- 前端：`sales/orders/dialogs.tsx` 的 `ConvertOrderModal`、`sales/service.ts` 的 `piApi.convert`；`purchase/requirements/index.tsx`、`purchase/orders/detail.tsx`；`finance/fx-settlements/index.tsx` 登记表单。
- 结汇后端接口与计算不变。
