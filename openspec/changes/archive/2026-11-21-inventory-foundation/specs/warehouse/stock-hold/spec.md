## REMOVED Requirements

### Requirement: 闲置库存
**Reason**: 库存模块上线后，多发暂存、订单取消多出来的货就是没有预占的可用库存，不再单独记一张闲置库存表。
**Migration**: 上线时把「暂存中」的闲置库存转成公司仓库的可用库存（期初流水）。处置改用标准单据：报废、转样品用其他出库单（见 `warehouse/other-outbound`），退回供应商用采购退货单的「库存退回」（见 `purchase/purchase-return`）。闲置库存页转到库存查询（只看可用），历史记录保留只读。

### Requirement: 售后退货转入闲置库存
**Reason**: 售后退货验收时已按退货批次入库，去向「转可用库存」就是把货留在可用库存，不再生成闲置库存。
**Migration**: 退货去向「转闲置库存」改名「转可用库存」（见 `warehouse/sales-return`「退货去向」），订单成本冲减口径不变（见 `finance/order-cost`）。已有的售后退货闲置库存随第一条一起转成可用库存。
