## Why

采购单编辑页表头可以直接换供应商，但它不会同步采购需求的「向谁买」，也不会合并到已有草稿；而行上的「改到其他供应商」这两点都会处理。两套换法并存，结果不一致。

## What Changes

- 去掉采购单编辑页表头的供应商下拉，采购对象只显示、不在编辑里改；草稿时提示「勾选型号点『改到其他供应商』，全选就是整单换」。
- 保存采购单的接口不再接受换供应商（去掉 `supplierId`）。
- 「改到其他供应商」改为同步需求的「向谁买」：被移动型号的需求建议渠道改为新采购对象。

## Capabilities

### New Capabilities
（无）

### Modified Capabilities
- `purchase/purchase-order`：「确认下单与修改」中草稿不再修改采购对象，换采购对象统一走「改到其他供应商」。

## Impact

- 后端：`SavePurchaseOrderRequest` 去掉 `supplierId`；`moveItems` 同步需求的建议渠道。
- 前端：采购单详情编辑卡片。
