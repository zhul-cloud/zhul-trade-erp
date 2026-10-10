## MODIFIED Requirements

### Requirement: 占位符约定与校验
系统 SHALL 约定 xlsx 模版用 `${字段}` 占位符：表头类字段（如 `${quotation.no}`、`${quotation.date}`、`${customer.name}`、`${quotation.currency}`、`${quotation.total}`）可放在任意单元格；明细行用 `${item.字段}`（如 `${item.no}`、`${item.model}`、`${item.brand}`、`${item.condition}`、`${item.leadTime}`、`${item.warranty}`、`${item.qty}`、`${item.unitPrice}`、`${item.amount}`），所在行即明细模版行，导出时按实际行数复制，引用明细区域的合计公式随之扩展；费用行用 `${fee.name}`、`${fee.amount}`（PI 另有 `${fee.remark}`，整单折扣作为一行负数费用输出）。PI 模版另有：买方 `${buyer.name}`、`${buyer.address}`、`${buyer.taxId}`、`${buyer.contact}`、`${buyer.phone}`、`${buyer.email}`；收货人 `${consignee.name}`、`${consignee.address}`、`${consignee.contact}`、`${consignee.phone}`；PI 信息 `${pi.no}`、`${pi.date}`、`${pi.deliveryTime}`、`${pi.paymentTerm}`、`${pi.incoterm}`、`${pi.portOfShipment}`、`${pi.remark}`、`${pi.currency}`、`${pi.total}`、`${pi.discount}`；卖方 `${seller.name}`、`${seller.email}`；收款账户 `${bank.name}`、`${bank.accountName}`、`${bank.accountNo}`、`${bank.swift}`、`${bank.country}`、`${bank.address}`、`${bank.bankCode}`、`${bank.branchCode}`；明细行另有 `${item.hsCode}`、`${item.origin}`、`${item.remark}`。文字报价模版用同样的占位符，明细用 `{{#items}} … {{/items}}` 包围的块逐行重复。上传时系统 SHALL 校验：不认识的占位符、报价单模版缺少明细行占位符，都拒绝保存并列出问题所在的单元格或行号；PI 模版同样校验（缺少明细行同样拒绝），CI、PL 模版在各自功能实现前只校验文件能否打开。系统提供每类单据的「占位符说明」与可下载的示例模版。

#### Scenario: 未知占位符
- **WHEN** 上传的报价单模版 C4 单元格写了 `${quotation.numbr}`
- **THEN** 系统拒绝保存并提示「C4：不认识的占位符 ${quotation.numbr}」

#### Scenario: 缺少明细行
- **WHEN** 上传的报价单模版中没有任何 `${item.…}` 占位符
- **THEN** 系统提示「没有找到明细行：请在型号行写上 ${item.model} 等占位符」，不保存

#### Scenario: PI 模版占位符
- **WHEN** 管理员上传的 PI 模版买方单元格写了 `${buyer.name}`、银行单元格写了 `${bank.accountNo}`、型号行写了 `${item.hsCode}`
- **THEN** 校验通过，导出 PI 时这些单元格分别填入买方名称、所选收款账户账号与 HS 编码

### Requirement: 按租户维护与初始模版
单据模版 SHALL 按租户隔离：每个租户维护自己的模版与默认版本，互不可见。新租户开通时，五类单据各自带一个通用的 V1 示例模版（不含任何公司抬头、Logo，抬头处是「YOUR COMPANY NAME」之类的普通文字；公司抬头、Logo、地址、公章与签名图片直接写在本公司上传的模版里，不由系统字段填充；收款银行信息由系统「收款账户」通过 `${bank.…}` 填充）并设为默认，租户管理员可以随时上传本公司版本替换。报价单、文字报价与 PI 已实现生成；CI、PL 的模版只做管理，生成与导出随后续发货功能提供。

#### Scenario: 新租户的初始模版
- **WHEN** 新租户的管理员第一次打开「单据模版」
- **THEN** 看到五类单据各有一个默认的 V1 示例模版，CI、PL 标注「生成功能随发货模块上线」

#### Scenario: 租户隔离
- **WHEN** 租户甲把报价单 V3 设为默认
- **THEN** 租户乙的报价单默认版本不受影响，也看不到租户甲的模版
