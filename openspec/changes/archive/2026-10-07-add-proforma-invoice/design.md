## Context

- 报价单（`add-quotation-pricing`）已实现：报价行带采购成本、建议毛利率等快照，状态推进客户询盘；单据模版有 Excel 渲染（POI，明细行与费用行展开、公式扩展、图片下移）、LibreOffice 转 PDF / 图片、实时预览，PI 模版目前只能上传管理。
- 客户档案已有「单证主体」（`customer_party`：收货人 / 通知方 / 发票抬头，每类一条默认），商品主数据已有 `hsCode`、`originCountry`。
- 现有编号：客户询盘 `IQ` + 日期 + 3 位流水（查当天最大号 + 唯一键冲突重试），报价单 `QT` 同法。
- 公司现有 317 份手工 PI：明细列以「型号 / 数量 / 单价 / 小计 / 备注」为主，几乎每份都有 Shipping Cost 与 Bank Charge 两行，折扣写成负数行；模版含 Logo、公章与签名图片。

## Goals / Non-Goals

**Goals:**
- 报价单 → PI → 销售订单打通，行级可追溯，报价单按行判断成交。
- PI 多版本可对比；收款按「水单 / 到账」分开登记，权限分离。
- 编号规则一次定好，供后续采购、发货、应收直接复用。

**Non-Goals:**
- 借项 / 贷项通知单、对账单、预收核销（应收模块）。
- 订单之后的采购、备货、发货、CI / PL 生成。
- PI 的审批流；多币种混合 PI。

## Decisions

### 1. 模块与数据模型
新模块 `com.zhul.erp.modules.sales`：
- `proforma_invoice`：编号、客户、业务员、币种、汇率快照、状态（1 草稿、2 已发送、3 已转订单、4 已作废）、当前有效版本号、编辑中的版本号、收款状态（缓存，按收款记录重算）。
- `proforma_invoice_version`：每个版本一行，保存该版本的表头快照（买方、收货人、交期、付款条件、贸易术语、起运港、备注、收款账户快照、折扣方式与值、各合计、发送时间与方式）。
- `proforma_invoice_item` / `proforma_invoice_fee`：挂在版本上（`version_id`），型号行记录 `quotation_item_id`、`quotation_id`、`inquiry_item_id`，并保存采购成本、建议毛利率、红线快照。
- `payment_receipt`：挂在 PI 上，`kind`（1 水单、2 到账），金额原币 / 本位币、汇率、日期、收款账户、关联水单、手续费差额、附件 key、状态（有效 / 作废）、操作人。
- `sales_order` / `sales_order_item` / `sales_order_fee`：从 PI 当前版本复制，`proforma_invoice_id` + `pi_version_no`，行记录 `pi_item_id`、`quotation_item_id`；状态（1 有效、2 已取消）、取消原因。
- `quotation` 增加状态 6「部分成交」；`quotation_item` 增加 `won`（是否进入有效订单）。

版本整体复制而不是只存差异：一张 PI 通常 3–10 行，复制成本低；对比时两份快照逐字段比较即可，实现最简单。
备选「只存当前内容 + 变更日志」——导出旧版本要回放日志，放弃。

### 2. 计算复用报价单
PI 行的金额、毛利率、净利润复用 `QuotationPricing` 的口径：PI 行一律视为「直接填外币售价」（单价来自报价单，可改），成本取快照。整单折扣：按百分比时 `discount = round(itemAmount × pct, 2, HALF_UP)`；合计净利润 = Σ行净利润 − 折扣折算（外币折扣直接减，人民币按 PI 汇率折算）；合计毛利率 = 1 − Σ成本 ÷ ((型号收入 − 折扣) × 汇率)。前端沿用 `calc.ts` 的同口径即时计算，保存时后端重算。

### 3. 编号服务
在 `modules.system` 新增 `DocumentNumberService.next(tenantId, DocType)`：用 `document_sequence(tenant_id, doc_type, biz_date, last_no)` 表，在独立事务（`REQUIRES_NEW`、读已提交）里执行 `INSERT … ON DUPLICATE KEY UPDATE last_no = last_no + 1`（插入或加一都持有行排他锁）再读回 `last_no`，遇到死锁重试至多 5 次（`INSERT IGNORE` 占位的做法在并发首次取号时会死锁，已用并发测试验证），替代「查最大号 + 冲突重试」；调用方事务回滚只会留下空号，不会重号。前缀从 `sys_config` 的 `document.number-prefix` 读取（租户覆盖、否则平台），仅对外类型拼接，与编号一起保存。报价单改用该服务（已有编号不变）；客户询盘暂不迁移，避免改动已上线逻辑。按编号搜索时，关键词若不以当前前缀开头则同时按「前缀 + 关键词」匹配。
备选：继续沿用查最大号 + 重试——高并发下要多次重试，放弃。

### 4. 状态联动与事务边界
- 开 PI：只记录关联，报价单状态不变（显示「已开 PI」由查询 PI 行得到）。
- 转订单 / 取消订单：同一事务内按顺序加锁——PI 行锁 → 来源报价单按 ID 升序行锁 → 客户询盘按 ID 升序行锁（复用 `InquiryStatusSync`）。报价单状态按「其型号行中有多少行出现在有效订单里」重算：全部 → 已成交，部分 → 部分成交，没有 → 已发送。询盘再按报价单状态推进（部分成交视同已成交）。
- 收款登记：锁 PI 行后写记录并重算收款状态，与转订单互斥，避免同时转两次。

### 5. 收款与权限
- 按钮权限：`sales:pi:receipt-slip`（上传水单，默认授给有 PI 菜单的角色）、`sales:pi:receipt-confirm`（登记到账，默认只授租户管理员，租户自行授给财务角色）。
- 手续费差额阈值存 `sys_config`（`sales.receipt.fee-tolerance`，默认 50，按 PI 币种金额判断）。
- 到账本位币用登记时的系统汇率快照；PI 本身的本位币金额仍用 PI 汇率，两者差异留给应收模块做汇兑损益。
- 水单附件走 `PrivateFileStorage`（模块 `payment-slip`），访问同 PI 的数据范围。

### 6. 收款账户
`tenant_bank_account`（币种、各字段、是否默认、状态），按租户隔离，没有平台默认。PI 版本保存账户快照（JSON）。日志与列表中的账号脱敏只显示后 4 位。

### 7. PI 模版与渲染
扩展 `Placeholders`：按单据类型区分可用占位符集合（报价单、PI 各一套），`XlsxTemplateInspector` 按类型校验。PI 的费用行包含运费、手续费与折扣（折扣以负数行输出，名称默认「Discount」）。公司《PI模版.xlsx》改造成占位符版：表头字段替换为 `${buyer.*}`、`${pi.*}`，银行区替换为 `${bank.*}`，保留 Logo、公章、签名图片（渲染时随明细展开下移）。

### 8. 前端
`pages/sales/pi`（列表、按报价单开 / 跨报价单挑型号、编辑页含版本切换与对比、收款面板）、`pages/sales/orders`（列表、详情）、`pages/system/bank-account`；报价单详情增加「开 PI」与关联 PI / 订单区；客户询盘、报价单、PI、订单详情统一的「来源 / 去向」链路组件。菜单新增一级「销售管理」。

## Risks / Trade-offs

- [凭水单转订单后钱迟迟不到] → 订单显示「待到账」并在列表突出；取消订单时报价单与询盘自动回退。
- [多张报价单汇率快照不同，合并后利润口径不一] → PI 统一用新建时的系统汇率重算本位币与利润，单价保持报价单的外币价格不变。
- [报价单状态改为自动成交，已手动标成交的历史数据] → 迁移时保留已有「已成交」记录不动（没有对应订单），新规则只作用于之后的变化。
- [版本整体复制占用空间] → 单张 PI 行数少，可忽略。
- [前缀规则改变报价单编号样式] → 只影响设置前缀之后的新单。

## Migration Plan

1. 迁移脚本：新表、`quotation` 状态注释与 `quotation_item.won`、`document_sequence`、`sys_config`（前缀、手续费阈值）、菜单与按钮权限、`tenant_bank_account`；以当前报价单当天最大号初始化 `document_sequence`，避免切换后撞号。
2. 福唯租户：设置前缀 `FW`，录入美元与人民币收款账户，上传占位符版 PI 模版并设为默认。
3. 回滚：下线销售管理菜单；报价单「部分成交」状态的单据人工改回已发送。
