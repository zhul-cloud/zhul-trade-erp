# document/document-template Specification

## Purpose
单据模版管理让报价单、PI、CI、PL 与文字报价这几类对外单据统一维护：每类单据按版本保存模版、指定一个默认版本，所有业务员导出时用的都是同一套格式，改版也有记录可追溯。

## Requirements

### Requirement: 单据模版类型与版本
系统 SHALL 内置五类单据模版：报价单（Quotation）、形式发票（PI）、商业发票（CI）、装箱单（PL）、文字报价。前四类上传 xlsx 文件（不超过 5 MB，按文件头校验类型），文字报价直接编辑文本（不超过 2000 字）。每类可以有多个版本，每个版本记录版本号（同类内自增，从 V1 开始）、版本说明（必填，不超过 200 字）、上传人与上传时间、状态（启用 / 停用）。版本内容保存后不能修改，需要调整时上传新版本。需要「单据模版」管理权限。

#### Scenario: 上传新版本
- **WHEN** 管理员为「报价单」上传新的 xlsx 并填写说明「更换公司新地址」
- **THEN** 生成报价单 V2，状态启用，原 V1 不变

#### Scenario: 文件类型不对
- **WHEN** 上传的文件扩展名是 xlsx 但实际是 PDF
- **THEN** 系统拒绝并提示「模版文件需要是 Excel（xlsx）」

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

### Requirement: 默认版本
每类单据 SHALL 有且仅有一个默认版本，所有业务员导出该类单据时一律使用默认版本，不能自选其他版本。管理员可以把任一启用的版本设为默认（二次确认，写操作日志）；默认版本不能停用，要先把别的版本设为默认。某类单据还没有任何版本时，导出提示「还没有可用的报价单模版，请联系管理员上传」。可以用指定版本对一张示例数据「预览导出」，便于设为默认前检查效果。

#### Scenario: 切换默认版本
- **WHEN** 管理员把报价单 V2 设为默认并确认
- **THEN** 之后所有业务员导出的报价单都使用 V2，操作日志记录由 V1 改为 V2

#### Scenario: 停用默认版本
- **WHEN** 管理员尝试停用当前默认的 V2
- **THEN** 系统提示「这是当前默认版本，请先把其他版本设为默认」，状态不变

### Requirement: 按租户维护与初始模版
单据模版 SHALL 按租户隔离：每个租户维护自己的模版与默认版本，互不可见。新租户开通时，五类单据各自带一个通用的 V1 示例模版（不含任何公司抬头、Logo，抬头处是「YOUR COMPANY NAME」之类的普通文字；公司抬头、Logo、地址、公章与签名图片直接写在本公司上传的模版里，不由系统字段填充；收款银行信息由系统「收款账户」通过 `${bank.…}` 填充）并设为默认，租户管理员可以随时上传本公司版本替换。报价单、文字报价与 PI 已实现生成；CI、PL 的模版只做管理，生成与导出随后续发货功能提供。

#### Scenario: 新租户的初始模版
- **WHEN** 新租户的管理员第一次打开「单据模版」
- **THEN** 看到五类单据各有一个默认的 V1 示例模版，CI、PL 标注「生成功能随发货模块上线」

#### Scenario: 租户隔离
- **WHEN** 租户甲把报价单 V3 设为默认
- **THEN** 租户乙的报价单默认版本不受影响，也看不到租户甲的模版
