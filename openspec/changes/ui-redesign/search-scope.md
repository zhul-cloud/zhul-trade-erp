# 列表关键字搜索核对表

逐个读后端关键字查询条件整理（2026-11-20，`feat/ui-redesign` 分支，基于 develop 8b284d01）。

- **匹配方式**：除特别注明外，都是「包含匹配，整段文字一起匹配，不拆词」。
- **规范化写法**：去掉空格和所有符号、忽略大小写后再匹配（`MpnNormalizer`），所以「6ES7-214」和「6es7 214」都能搜到同一型号。
- **代码位置**：都在 `zhul-erp-backend/src/main/java/com/zhul/erp/modules/` 下。
- **改造时的写法**：输入框提示取「可搜范围」里最常用的至多三项，帮助图标列出全部可搜项。
- **「提示问题」列**：现在的输入框提示和后端实际搜索范围不一致的地方，改造时一并改正。

## 销售

| 页面 | 可搜范围（后端实际） | 代码位置 | 提示问题 |
|---|---|---|---|
| 销售订单 | 订单编号、PI 编号（不带前缀也能搜到）、客户名称或简称、型号 | `sales/service/impl/SalesOrderServiceImpl` keyword 条件 | 无 |
| 形式发票 | PI 编号、客户名称或简称、型号 | `sales/service/impl/PiServiceImpl:1099` | 无 |
| 报价管理 | 报价单编号、客户名称或简称、型号（只搜当前版本的型号） | `quotation/service/impl/QuotationServiceImpl:1195` | 无 |
| 询盘管理 | 询盘编号、客户名称或联系人、型号（按规范化写法匹配） | `inquiry/customerinquiry/service/impl/CustomerInquiryServiceImpl:399` | 提示没写联系人 |
| 商机管理 | 商机编号；客户编号、名称、联系人、邮箱、WhatsApp | `crm/service/impl/OpportunityServiceImpl:373、772` | 无 |
| 客户跟进 | 客户名称、中文名、客户编号、单据编号 | `crm/followup/repository/FollowupItemMapper:48` | 无 |
| 客户公海 | 客户名称、中文名、客户编号、联系人 | `masterdata/service/impl/CustomerServiceImpl:949` | 无 |
| 售后管理 | 售后单号、订单编号、型号 | `aftersales/service/AfterSalesService:440` | 无 |
| 销售退货 | 退货单号、快递单号、订单编号、售后单号、型号 | `aftersales/service/SalesReturnService:355` | 无 |

## 采购

| 页面 | 可搜范围（后端实际） | 代码位置 | 提示问题 |
|---|---|---|---|
| 采购订单 | 采购订单编号、店铺名称、供应商名称或简称、销售订单编号、型号 | `purchase/service/impl/PurchaseOrderServiceImpl:213` | 无 |
| 采购需求 | 销售订单编号、客户名称或简称、型号、描述 | `purchase/service/impl/PurchaseRequirementServiceImpl:134` | 无 |
| 发货跟踪 | 发货单号、快递单号；采购订单编号、店铺名称、供应商名称或简称、采购订单型号 | `warehouse/service/ShipmentService:114`、`warehouse/support/ReceivingSupport:105` | 无 |
| 采购退货 | 退货单号、快递单号、型号、退货入库单号、采购入库单号；采购订单编号、店铺、供应商、采购订单型号 | `purchase/purchasereturn/service/PurchaseReturnService:569` | 提示只写了单号、型号、快递单号，漏了采购订单和供应商 |
| 询价分配、我的询价 | 任务编号、品牌、品类、询盘编号、型号（确认型号、原始型号、规范化写法） | `inquiry/sourcing/service/impl/TaskKeywordMatcher` | 提示没写品类 |

## 仓库与物流

| 页面 | 可搜范围（后端实际） | 代码位置 | 提示问题 |
|---|---|---|---|
| 采购入库 | 采购入库单号；发货单号、快递单号；采购订单编号、店铺、供应商、采购订单型号 | `warehouse/service/ReceiptService:123` | 列表页没有提示文字 |
| 销售出库 | 销售出库单号、销售订单编号、出库型号、国内快递单号 | `logistics/service/OutboundService:659、952` | **提示写了「客户」，后端不搜客户**；漏了快递单号 |
| 退货入库 | 退货入库单号、订单编号、退货单号、退货快递单号、售后单号、型号 | `aftersales/service/ReturnReceiptService:449` | 无 |
| 闲置库存 | 型号、品牌；采购订单编号、店铺、供应商、采购订单型号；销售订单编号 | `warehouse/service/HoldService:65` | 提示只写了型号、来源单号，漏了品牌 |
| 实拍任务 | 型号、品牌、采购入库单号、销售订单编号 | `warehouse/service/ShootService:139` | 无 |
| 到货差异 | 型号、采购入库单号；采购订单编号、店铺、供应商、采购订单型号 | `warehouse/service/DiscrepancyService:164` | 待核实页面位置和提示 |
| 出运管理 | 出运单号、运单号、销售订单编号、客户 | `logistics/service/LogisticsShipmentService:551、840` | 客户按哪些字段匹配**待核实** |
| 货代管理 | 名称、简称、联系人、联系电话 | `logistics/service/ForwarderService:69` | 提示没写电话 |

## 财务

| 页面 | 可搜范围（后端实际） | 代码位置 | 提示问题 |
|---|---|---|---|
| 收款管理 · 待确认 | PI 编号、订单编号、客户名称或简称 | `sales/service/impl/PaymentReceiptServiceImpl:650` | 提示没写订单编号；「PI 带不带前缀都可」**待核实**，此处代码是普通包含匹配 |
| 收款管理 · 收款单 | 收款单号、平台订单号、付款人、PI 编号、订单编号、客户名称、简称或联系人 | `sales/service/impl/ReceiptLedgerServiceImpl:248` | 提示没写联系人 |
| 收款管理 · 未认领到账 | **待核实** | — | — |
| 退款管理 | 退款单号、订单编号、客户名称 | `finance/service/CustomerRefundService:527` | 无 |
| 退款申请 | 申请号、退货单号、订单编号、售后单号 | `finance/service/RefundRequestService:480` | 无 |
| 余额管理 | 客户名称（不区分大小写） | `finance/service/CustomerCreditService:107` | 无 |
| 应付管理 | 应付单号、收款方、来源单号 | `finance/service/PayableQueryService:74` | 无 |
| 付款管理 | 付款单号、收款方 | `finance/service/PaymentService:675` | 无 |
| 报销管理 | 报销单号、提交人 | `finance/service/ReimbursementService:731` | 无 |
| 费用管理 | 费用编号、名称、收款方 | `finance/service/ExpenseService:680` | 无 |

## 商品与运营

| 页面 | 可搜范围（后端实际） | 代码位置 | 提示问题 |
|---|---|---|---|
| 商品档案 | 商品名称、型号（含规范化写法）、订货号 | `product/service/impl/ProductServiceImpl:178` | 提示没写订货号 |
| 商品候选 | 型号（含规范化写法）、品牌文字、商品名称、订货号 | `product/candidate/service/ProductCandidateService:148` | 提示没写订货号 |
| 品牌管理 | 品牌名称、别名 | `product/service/impl/BrandServiceImpl:72` | 无 |
| 品类管理 | 品类编码、英文名、中文名 | `product/service/impl/CategoryServiceImpl:70` | 列表页没有提示文字 |
| 系列管理 | 系列名称 | `product/service/impl/SeriesServiceImpl:63` | 列表页没有提示文字 |
| 资料任务 | 商品名称、型号（含规范化写法） | `product/content/repository/ProductContentTaskMapper:42` | 无 |
| 发品任务 | 商品名称、型号（含规范化写法） | `listing/repository/ListingTaskMapper:32` | 无 |
| 素材管理 | 品牌、型号 | `media/repository/MediaItemMapper:16` | 无 |

## 尚未核对

以下页面的关键字在前端或其他服务里处理，改到对应批次时补核：
- 供应链各页（`supply-chain/*`，后端 `supply/service/*`）；
- 系统管理各页（部门、字典、菜单、角色、配置）；
- 客户管理主列表（按 `name` 参数匹配名称、中文名、简称，`CustomerServiceImpl:614`）；
- 各弹窗内的选择器搜索。
