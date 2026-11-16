# logistics/shipping-documents Specification

## Purpose
在出运单上按订单合成一组或每张订单各一组生成商业发票 CI 与装箱单 PL：CI 取订单型号、英文描述、HS 编码、单价与费用行，PL 取装箱记录并按箱合并单元格；CI 与 PL 共用编号主体，重新生成编号不变，可导出 Excel 与 PDF。

## Requirements

### Requirement: 单证组与 CI、PL 生成
业务员 SHALL 能在出运单上生成单证组：一张出运单可以合成一组（全部订单），也可以每张订单各一组。选「每张订单各一组」时，同一合并打包组里的几张订单共用箱子，自动合成一组，其余订单仍各一组。每组有一张商业发票（CI）与一张装箱单（PL），编号按「单据编号规则」生成并共用主体（如 `FWCI20261014001` 与 `FWPL20261014001`）。
- CI：取订单的型号、英文描述、HS 编码、原产地、数量（本出运单上的数量）、单价、金额，订单的费用行（如运费），币种与合计，买方与收货人（取订单）、付款参考（选填，如 TT 号）；订单行没有记 HS 编码、原产地时取商品档案的报关信息；
- 寄件方（Shipper）同 PI，取第一张订单业务员的姓名、邮箱、电话；
- 订单上的买方、收货人没有详细地址时，改用客户档案里现在的默认发票抬头、默认收货人（收货人也没有时用买方）；联系人、电话、邮箱为空的用客户主联系人补上；
- PL：按箱输出，箱内每个型号一行，箱的净重、毛重、尺寸、体积写在箱内第一行并合并单元格；合并打包组的箱子只列一次，箱内各订单的型号都列出；合计件数、净重、毛重、体积；附带 PI 号、CI 号、价格条款；
- 导出 Excel 与 PDF，使用「单据模板」的默认版本；生成后可以重新生成（编号不变），出运单作废时单证组随之作废。
- 明细行的文字（如英文描述）加宽到列宽上限仍放不下时自动换行，并按行数加高这一行；PDF 里的图片按 300 dpi 保留，公章、签名不发糊（报价单、PI 仍按 150 dpi 压缩）。
- 导出前检查必填信息，缺的时候不导出，提示缺什么、去哪里补：两种单证都要寄件方姓名、邮箱、电话，收货人名称、详细地址、联系人、电话；CI 另要每个型号的 HS 编码，PL 另要收货人邮箱。

#### Scenario: 两张订单合成一组
- **WHEN** 出运单含 SO1、SO2 的销售出库单，业务员选「合成一组」
- **THEN** 生成 FWCI20261014001 与 FWPL20261014001，CI 列出两张订单本次出运的型号，PL 列出全部箱子

#### Scenario: 每张订单各一组
- **WHEN** 业务员选「每张订单各一组」
- **THEN** 生成两组 CI/PL，编号流水各不相同，各自只含本订单的型号与箱子

#### Scenario: 合并打包的订单合成一组
- **GIVEN** 出运单含 SO1、SO2、SO3 的销售出库单，其中 SO1、SO2 的出库单合并打包
- **WHEN** 业务员选「每张订单各一组」
- **THEN** 生成两组：SO1 + SO2 一组（PL 列出两张订单共用的箱子，每箱只列一次），SO3 一组

#### Scenario: 缺信息时提示补全
- **WHEN** 导出客户 ACROBOT 的 CI 时，收货人只有国家、没有详细地址，型号 6ES7321-1FH00-0AA0 在订单和商品档案里都没有 HS 编码
- **THEN** 不导出，提示「导出前请先补全：收货人的详细地址：在客户「ACROBOT」档案的「收货与单证信息」里补全；型号 6ES7321-1FH00-0AA0 的 H.S Code：在商品档案的「报关信息」里补全」

### Requirement: CI 与 PL 模版占位符
CI 模版 SHALL 支持：`${ci.no}`、`${ci.date}`、`${ci.currency}`、`${ci.total}`、`${ci.paymentRef}`、`${pi.no}`、`${so.no}`、`${doc.note}`（售后补发说明，没有时为空）、买方 `${buyer.*}` 与收货人 `${consignee.*}`（同 PI，另有 `${buyer.whatsapp}`、`${consignee.whatsapp}`、`${customer.whatsapp}`，取客户档案的 WhatsApp）、寄件方 `${seller.name}`、`${seller.email}`、`${seller.phone}`；明细 `${item.no}`、`${item.model}`、`${item.description}`、`${item.hsCode}`、`${item.origin}`、`${item.qty}`、`${item.unitPrice}`、`${item.amount}`；费用 `${fee.no}`、`${fee.name}`、`${fee.amount}`。PL 模版 SHALL 支持：`${pl.no}`、`${pl.date}`、`${pl.priceTerm}`、`${pl.totalBoxes}`、`${pl.totalQty}`、`${pl.totalNetWeight}`、`${pl.totalGrossWeight}`、`${pl.totalVolume}`、`${ci.no}`、`${pi.no}`、`${doc.note}`、买方与收货人（含 WhatsApp）、寄件方；明细 `${item.no}`、`${item.model}`、`${item.description}`、`${item.qty}`、`${box.no}`、`${box.netWeight}`、`${box.grossWeight}`、`${box.dimensions}`（如 55x40x30）、`${box.volume}`（立方米，三位小数）。上传 CI、PL 模版时按以上占位符校验，缺少明细行占位符拒绝保存。

#### Scenario: PL 模版缺少明细行
- **WHEN** 上传的 PL 模版里没有任何 `${item.…}` 占位符
- **THEN** 系统拒绝保存，提示「PL 模版缺少明细行占位符」

### Requirement: 补发订单的申报货值
单证组包含售后补发订单的型号时，CI SHALL 按型号的申报单价计算金额（单价 × 数量，HALF_UP 两位）；CI 与 PL 中补发型号的描述后注明「(Free of charge replacement, no commercial value)」，并提供表头占位符 `${doc.note}`（单证组含补发订单时为该说明，否则为空）供自定义模版使用。同一单证组里普通订单与补发订单的型号分别按各自单价计算。

#### Scenario: 补发订单出 CI
- **WHEN** 补发订单 SO20261103001 的 HG-SN202BJ-S100 × 1 申报单价 USD 441.10，生成 CI
- **THEN** CI 该行单价 USD 441.10、金额 USD 441.10，描述后注明「(Free of charge replacement, no commercial value)」
