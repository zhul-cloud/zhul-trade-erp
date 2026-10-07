## MODIFIED Requirements

### Requirement: 占位符约定与校验
系统 SHALL 约定 xlsx 模版用 `${字段}` 占位符：表头类字段（如 `${quotation.no}`、`${quotation.date}`、`${customer.name}`、`${quotation.currency}`、`${quotation.total}`）可放在任意单元格；明细行用 `${item.字段}`（如 `${item.no}`、`${item.model}`、`${item.brand}`、`${item.condition}`、`${item.leadTime}`、`${item.warranty}`、`${item.qty}`、`${item.unitPrice}`、`${item.amount}`），所在行即明细模版行，导出时按实际行数复制，引用明细区域的合计公式随之扩展；费用行用 `${fee.no}`（接着型号行的序号）、`${fee.name}`、`${fee.amount}`（PI 另有 `${fee.remark}`，整单折扣作为一行负数费用输出）。整格只有一个数字类占位符时写成数字单元格，不残留占位符原文。PI 模版另有：买方 `${buyer.name}`、`${buyer.address}`、`${buyer.taxId}`、`${buyer.contact}`、`${buyer.phone}`、`${buyer.email}`；收货人 `${consignee.name}`、`${consignee.address}`、`${consignee.contact}`、`${consignee.phone}`；PI 信息 `${pi.no}`、`${pi.date}`、`${pi.deliveryTime}`、`${pi.paymentTerm}`、`${pi.incoterm}`、`${pi.portOfShipment}`、`${pi.remark}`、`${pi.currency}`、`${pi.total}`、`${pi.discount}`；卖方 `${seller.name}`、`${seller.email}`；收款账户 `${bank.name}`、`${bank.accountName}`、`${bank.accountNo}`、`${bank.swift}`、`${bank.country}`、`${bank.address}`、`${bank.bankCode}`、`${bank.branchCode}`；明细行另有 `${item.hsCode}`、`${item.origin}`、`${item.remark}`。文字报价模版用同样的占位符，明细用 `{{#items}} … {{/items}}` 包围的块逐行重复。上传时系统 SHALL 校验：不认识的占位符、报价单模版缺少明细行占位符，都拒绝保存并列出问题所在的单元格或行号；PI 模版同样校验（缺少明细行同样拒绝），CI、PL 模版在各自功能实现前只校验文件能否打开。系统提供每类单据的「占位符说明」与可下载的示例模版。

#### Scenario: 未知占位符
- **WHEN** 上传的报价单模版 C4 单元格写了 `${quotation.numbr}`
- **THEN** 系统拒绝保存并提示「C4：不认识的占位符 ${quotation.numbr}」

#### Scenario: 缺少明细行
- **WHEN** 上传的报价单模版中没有任何 `${item.…}` 占位符
- **THEN** 系统提示「没有找到明细行：请在型号行写上 ${item.model} 等占位符」，不保存

#### Scenario: PI 模版占位符
- **WHEN** 管理员上传的 PI 模版买方单元格写了 `${buyer.name}`、银行单元格写了 `${bank.accountNo}`、型号行写了 `${item.hsCode}`
- **THEN** 校验通过，导出 PI 时这些单元格分别填入买方名称、所选收款账户账号与 HS 编码
