## ADDED Requirements

### Requirement: 补发订单的申报货值
单证组包含售后补发订单的型号时，CI SHALL 按型号的申报单价计算金额（单价 × 数量，HALF_UP 两位）；CI 与 PL 中补发型号的描述后注明「(Free of charge replacement, no commercial value)」，并提供表头占位符 `${doc.note}`（单证组含补发订单时为该说明，否则为空）供自定义模版使用。同一单证组里普通订单与补发订单的型号分别按各自单价计算。

#### Scenario: 补发订单出 CI
- **WHEN** 补发订单 SO20261103001 的 HG-SN202BJ-S100 × 1 申报单价 USD 441.10，生成 CI
- **THEN** CI 该行单价 USD 441.10、金额 USD 441.10，描述后注明「(Free of charge replacement, no commercial value)」

## MODIFIED Requirements

### Requirement: CI 与 PL 模版占位符
CI 模版 SHALL 支持：`${ci.no}`、`${ci.date}`、`${ci.currency}`、`${ci.total}`、`${ci.paymentRef}`、`${pi.no}`、`${so.no}`、`${doc.note}`（售后补发说明，没有时为空）、买方 `${buyer.*}` 与收货人 `${consignee.*}`（同 PI）；明细 `${item.no}`、`${item.model}`、`${item.description}`、`${item.hsCode}`、`${item.origin}`、`${item.qty}`、`${item.unitPrice}`、`${item.amount}`；费用 `${fee.no}`、`${fee.name}`、`${fee.amount}`。PL 模版 SHALL 支持：`${pl.no}`、`${pl.date}`、`${pl.priceTerm}`、`${pl.totalBoxes}`、`${pl.totalQty}`、`${pl.totalNetWeight}`、`${pl.totalGrossWeight}`、`${pl.totalVolume}`、`${ci.no}`、`${pi.no}`、`${doc.note}`、买方与收货人；明细 `${item.no}`、`${item.model}`、`${item.description}`、`${item.qty}`、`${box.no}`、`${box.netWeight}`、`${box.grossWeight}`、`${box.dimensions}`（如 55x40x30）、`${box.volume}`（立方米，三位小数）。上传 CI、PL 模版时按以上占位符校验，缺少明细行占位符拒绝保存。

#### Scenario: PL 模版缺少明细行
- **WHEN** 上传的 PL 模版里没有任何 `${item.…}` 占位符
- **THEN** 系统拒绝保存，提示「PL 模版缺少明细行占位符」
