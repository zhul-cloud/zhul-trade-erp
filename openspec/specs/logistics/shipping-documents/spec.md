# logistics/shipping-documents Specification

## Purpose
在出运单上按订单合成一组或每张订单各一组生成商业发票 CI 与装箱单 PL：CI 取订单型号、英文描述、HS 编码、单价与费用行，PL 取装箱记录并按箱合并单元格；CI 与 PL 共用编号主体，重新生成编号不变，可导出 Excel 与 PDF。

## Requirements

### Requirement: 单证组与 CI、PL 生成
业务员 SHALL 能在出运单上生成单证组：一张出运单可以合成一组（全部订单），也可以每张订单各一组。每组有一张商业发票（CI）与一张装箱单（PL），编号按「单据编号规则」生成并共用主体（如 `FWCI20261014001` 与 `FWPL20261014001`）。
- CI：取订单的型号、英文描述、HS 编码、原产地、数量（本出运单上的数量）、单价、金额，订单的费用行（如运费），币种与合计，买方与收货人（取订单）、付款参考（选填，如 TT 号）；
- PL：按箱输出，箱内每个型号一行，箱的净重、毛重、尺寸、体积写在箱内第一行并合并单元格；合计件数、净重、毛重、体积；附带 PI 号、CI 号、价格条款；
- 导出 Excel 与 PDF，使用「单据模版」的默认版本；生成后可以重新生成（编号不变），出运单作废时单证组随之作废。

#### Scenario: 两张订单合成一组
- **WHEN** 出运单含 SO1、SO2 的出库单，业务员选「合成一组」
- **THEN** 生成 FWCI20261014001 与 FWPL20261014001，CI 列出两张订单本次出运的型号，PL 列出全部箱子

#### Scenario: 每张订单各一组
- **WHEN** 业务员选「每张订单各一组」
- **THEN** 生成两组 CI/PL，编号流水各不相同，各自只含本订单的型号与箱子

### Requirement: CI 与 PL 模版占位符
CI 模版 SHALL 支持：`${ci.no}`、`${ci.date}`、`${ci.currency}`、`${ci.total}`、`${ci.paymentRef}`、`${pi.no}`、`${so.no}`、买方 `${buyer.*}` 与收货人 `${consignee.*}`（同 PI）；明细 `${item.no}`、`${item.model}`、`${item.description}`、`${item.hsCode}`、`${item.origin}`、`${item.qty}`、`${item.unitPrice}`、`${item.amount}`；费用 `${fee.no}`、`${fee.name}`、`${fee.amount}`。PL 模版 SHALL 支持：`${pl.no}`、`${pl.date}`、`${pl.priceTerm}`、`${pl.totalBoxes}`、`${pl.totalQty}`、`${pl.totalNetWeight}`、`${pl.totalGrossWeight}`、`${pl.totalVolume}`、`${ci.no}`、`${pi.no}`、买方与收货人；明细 `${item.no}`、`${item.model}`、`${item.description}`、`${item.qty}`、`${box.no}`、`${box.netWeight}`、`${box.grossWeight}`、`${box.dimensions}`（如 55x40x30）、`${box.volume}`（立方米，三位小数）。上传 CI、PL 模版时按以上占位符校验，缺少明细行占位符拒绝保存。

#### Scenario: PL 模版缺少明细行
- **WHEN** 上传的 PL 模版里没有任何 `${item.…}` 占位符
- **THEN** 系统拒绝保存，提示「PL 模版缺少明细行占位符」
