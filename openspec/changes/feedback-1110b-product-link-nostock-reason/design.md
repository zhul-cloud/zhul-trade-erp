## Context

- 商品档案（`product`）是平台共享数据，按品牌 + 归一化型号唯一。`ProductLookupService` 已有两种查法：`search`（按型号前缀、产品名称、订货号搜，只返回启用未删除的商品）和 `match`（按品牌 + 型号精确匹配）。
- 询盘型号（`inquiry_item`）确认时自动建档，记有 `product_id`、`order_code`、`category`。报价单、PI、销售订单的明细都通过 `inquiry_item_id` 追溯到询盘型号；手动订单的明细没有这个关联，型号与品牌是自由文本。
- 前端 `ProductSelect` 已被询盘等模块复用，返回 `ProductOption`（id、展示型号、品牌、品类）。
- 无货结果存于 `sourcing_quote`（`no_stock = 1`，说明写在 `note`）。报价单另有一套给客户看的英文原因 `quotation_item.no_stock_reason`（0–4 固定枚举）。
- 系统字典是 `dict_type` / `dict_item`，内置项用迁移以 `tenant_id = 0` 写入，业务代码通过 `DictTypes` 常量引用，读取时用 `QuoteDicts` 一类的快照。
- 客户列组件 `CustomerCell` / `CustomerSub` 在 `components/DocFields`，十一个列表在用；出运单、退货入库只显示客户名称。
- 权限：前端 `access.productList`（商品档案菜单）、`access['product:product:add']`（新建商品按钮）。

## Goals / Non-Goals

**Goals:**
- 新的手动订单每行都关联有效的商品档案。
- 四张单据的型号明细视觉一致、能跳到商品档案并返回。
- 无货原因结构化、可统计、可由管理员维护。
- 客户列在窄列宽下仍清晰。

**Non-Goals:**
- 不回填已有手动订单的商品档案 ID（显示时用品牌 + 型号匹配兜底）。
- 不改发给客户的 PDF、Excel、文字报价版式。
- 不改报价单给客户的英文无货原因，也不根据内部原因自动生成英文原因。
- 不改 PI 手动添加行、报价单手动添加行的录入方式（只改显示）。
- 不在本次做无货原因统计报表；字段结构化后可以在询价统计里再加。

## Decisions

### 1. 订单明细存商品档案 ID，品牌、品类以档案为准
- `sales_order_item` 新增 `product_id`（bigint，可空，加索引）。手动建单请求的型号行改为传 `productId`，后端按 ID 读商品档案，校验存在、未删除、已启用，再用档案的展示型号、品牌名、品类名写入 `model`、`brand`、`category`。前端传来的型号、品牌文字一律忽略。
- 理由：采购、出运、利润统计都读 `model` / `brand` 文本，保留这些冗余字段可以不改下游；以后端读档案为准，避免前端篡改。
- 备选：只存 `product_id`、不存文本——下游改动面太大，不采用。

### 2. 型号明细共用一个前端组件
- 在 `components/DocFields` 新增 `DocModelCell`：入参 `model`、`orderCode`、`brand`、`productId`、`from`（返回信息）。有 `productId` 且 `access.productList` 时型号渲染为链接，否则为文字；下面显示订货号与已有的 `ViewMediaLink`。
- 品牌、品类单独成列，「来源询盘」列显示 `inquiryCode` 链接。询盘单只把型号文字换成该组件，保留按组显示品牌的现有逻辑。

### 3. 后端统一补全明细的商品档案信息
- 新增一个小的支持类（如 `DocItemProductResolver`），给一批明细行补 `productId`、`orderCode`、`category`：
  1. 行自身有 `product_id`（手动订单）直接用；
  2. 有 `inquiry_item_id` 的，一次批量查询询盘型号取 `product_id`、`order_code`、`category`；
  3. 仍没有的，按「品牌 + 归一化型号」批量匹配商品档案（同 `match` 的精确规则，一次查询，不逐行查）。
- 报价单、PI、销售订单三个详情接口调用它，VO 增加 `productId`（`QuotationItemVO` 已有 `orderCode`、`category`；`PiItemVO` 补 `orderCode`、`productId`，`SalesOrderItemVO` 继承）。
- 商品档案是平台共享表，按现有 `ProductWriteGuard` / 可读范围规则查询，不新增租户条件。

### 4. 返回原单据用查询参数
- 链接形如 `/product/products/123?from=/quotation/456&fromLabel=报价单 QT20261105001`。商品详情页读到 `from` 时，只接受以 `/` 开头、不以 `//` 开头的站内路径，按钮文字为「返回 + fromLabel」，点后 `history.push(from)`；没有参数时保持「返回商品列表」。
- 用当前页跳转（`history.push`），浏览器后退天然回到原单据；报价单草稿的未保存提示复用页面现有的 `history.block`。
- 备选：新标签页打开——用户要求「可以返回前页面」，当前页跳转 + 返回按钮更符合。

### 5. 无货原因用新字典，存字典项编码
- 新字典类型 `inquiry_no_stock_reason`（`DictTypes.NO_STOCK_REASON`），预置项编码 `DISCONTINUED`、`OUT_OF_STOCK`、`NO_SOURCE`、`LONG_LEAD_TIME`、`OTHER`，值 1–5。`sourcing_quote` 新增 `no_stock_reason`（int，可空），存字典值，与货况、货期的存法一致。
- 在线录入（`ItemQuotesRequest.noStockReason`）：`noStock = true` 且提交时必填（缺少时提示「请选择无货原因」，保存草稿可不填）；填了的须是字典中启用的项，否则抛业务异常「无货原因无效，请重新选择」。粘贴回填只填进前端表单，提交时同样校验；只有 Excel 导入不传，存空。
- 展示：`QuoteDicts` 快照加载这个字典，各 VO 增加 `noStockReasonName`（停用项也按原名称解析；空值返回空，由前端显示「未填原因」）。涉及 `InquiryItemVO`、`MyQuoteVO`、`PriceRecordVO`、报价单候选与 `QuotationItemVO`（新增 `sourcingNoStockReasonName`、`sourcingNoStockNote`，与给客户的 `noStockReason` 区分命名）。
- 备选：复用报价单的 0–4 固定枚举——那是给客户的英文措辞，用户选择了内外分开，不采用。

### 6. 客户列三行
- `CustomerCell` 改为纵向三行：名称 + 新老客户标签、国家、主联系人（联系人前保留 `UserOutlined` 小图标）；缺哪行不渲染哪行。`CustomerSub` 只在没有名称行的场景使用，同步改为两行。
- 出运单、退货入库列表接口补 `customerCountry`、`customerType`、`customerContact`，取法与销售订单列表一致（按客户 ID 批量查，不 JOIN 超过三张表）。

### 事务边界与精度
- 手动建单仍在 `SalesOrderServiceImpl` 原有事务内完成，读商品档案只读、不加锁；商品在读取后被停用的极小窗口不处理（建单以读取时状态为准）。
- 回价保存仍在 `saveQuotes` 原有事务内；本次不涉及金额计算，没有新增精度规则。

### 迁移 V1.5.24
- `sales_order_item.product_id`、`sourcing_quote.no_stock_reason` 两列加索引；字典类型与五个字典项按 `NOT EXISTS` 写入，可重复执行。
- 回退：删除两列与字典数据即可，旧代码不读这些字段。

## Risks / Trade-offs

- [商品档案里没有的型号，手动订单建不了] → 下拉提供「去商品档案新建」；没有新建权限的提示找同事。这是用户要求的约束。
- [品牌 + 型号匹配兜底可能匹配到同型号不同写法的品牌] → 只用精确规则（品牌名一致 + 归一化型号一致），匹配不到就显示文字，不做模糊匹配。
- [三张单据的明细多一个批量查询] → 一张单据只多两次批量查询（询盘型号、商品档案），行数有限，可接受。
- [历史无货没有原因] → 统一显示「未填原因」并附说明，不做数据回填。
- [导入的无货没有原因，统计会有缺口] → 采购可在线补选；后续若要统计再考虑在导入确认页加原因下拉。
