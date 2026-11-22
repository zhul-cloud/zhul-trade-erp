# 列表关键字搜索核对表

逐个读后端（及前端筛选）的关键字条件整理而成，2026-11-20，`feat/ui-redesign` 分支，已合入 develop f79ac93f。

- **匹配方式**：除特别注明外，都是「包含匹配，整段文字一起匹配，不拆词」。关联单据、客户、型号的命中数有上限（200 或 500 条）。除商品域外，输入里的 `%`、`_` 会被当作通配符。
- **型号规范化**：先全角转半角、忽略大小写，再只保留英文字母和数字去匹配（`MpnNormalizer`），所以「6ES7-214」和「6es7 214」能搜到同一个型号。这条只用于型号。
- **客户按「显示名」匹配**：有名称取名称，名称为空时取联系人名（`InquiryLookups.customerName`），不区分大小写。
- **代码位置**：后端都在 `zhul-erp-backend/src/main/java/com/zhul/erp/modules/` 下，前端在 `zhul-erp-frontend/src/pages/` 下。
- **改造时怎么写**：输入框提示只放「可搜范围」里最常用的至多三项，完整范围放在帮助图标里。「提示问题」一列记的是现有提示和实际搜索不一致的地方，改造时一并改正。

## 销售

| 页面 | 可搜范围 | 代码位置 | 提示问题 |
|---|---|---|---|
| 销售订单 | 订单编号、PI 编号（不带前缀也能搜到）、客户名称或简称、型号 | `sales/service/impl/SalesOrderServiceImpl` 关键字条件 | 无 |
| 形式发票 | PI 编号、客户名称或简称、型号 | `sales/service/impl/PiServiceImpl:1099` | 无 |
| 报价管理 | 报价单编号、客户名称或简称、型号（只搜当前版本） | `quotation/service/impl/QuotationServiceImpl:1195` | 无 |
| 询盘管理 | 询盘编号、客户名称或联系人、型号（规范化） | `inquiry/customerinquiry/service/impl/CustomerInquiryServiceImpl:399` | 漏了联系人 |
| 商机管理 | 商机编号；客户编号、名称、联系人、邮箱、WhatsApp | `crm/service/impl/OpportunityServiceImpl:373、772` | 无 |
| 客户管理 | 英文名、中文名、简称（客户编码、联系人另有输入框） | `masterdata/service/impl/CustomerServiceImpl:614` | 无 |
| 客户跟进 | 客户名称、中文名、客户编号、单据编号 | `crm/followup/repository/FollowupItemMapper:48` | 无 |
| 客户公海 | 客户名称、中文名、客户编号、联系人 | `masterdata/service/impl/CustomerServiceImpl:949` | 无 |
| 售后管理 | 售后单号、订单编号、型号 | `aftersales/service/AfterSalesService:440` | 无 |
| 销售退货 | 退货单号、快递单号、订单编号、售后单号、型号 | `aftersales/service/SalesReturnService:355` | 无 |

## 采购

| 页面 | 可搜范围 | 代码位置 | 提示问题 |
|---|---|---|---|
| 采购订单 | 采购订单编号、店铺名称、供应商名称或简称、销售订单编号、型号 | `purchase/service/impl/PurchaseOrderServiceImpl:213` | 无 |
| 采购需求 | 销售订单编号、客户名称或简称、型号、描述 | `purchase/service/impl/PurchaseRequirementServiceImpl:134` | 无 |
| 发货跟踪 · 发货单 | 发货单号、快递单号；采购订单编号、店铺、供应商名称或简称、采购订单型号 | `warehouse/service/ShipmentService:114`、`warehouse/support/ReceivingSupport:105` | 无 |
| 发货跟踪 · 到货差异 | 型号、采购入库单号；采购订单编号、店铺、供应商、采购订单型号 | `warehouse/service/DiscrepancyService:164`（页面 `purchase/shipments/index.tsx` 的到货差异页签） | 无 |
| 采购退货 | 退货单号、快递单号、型号、退货入库单号、采购入库单号；采购订单编号、店铺、供应商、采购订单型号 | `purchase/purchasereturn/service/PurchaseReturnService:569` | 漏了采购订单和供应商 |
| 询价分配、我的询价 | 任务编号、品牌、品类、询盘编号、型号（确认型号、原始型号、规范化） | `inquiry/sourcing/service/impl/TaskKeywordMatcher` | 漏了品类 |

## 仓库与物流

| 页面 | 可搜范围 | 代码位置 | 提示问题 |
|---|---|---|---|
| 采购入库 · 待收货 | 发货单号、快递单号；采购订单编号、店铺、供应商、采购订单型号 | `ShipmentService.page`（`warehouse/controller/ReceiptController:49`） | 漏了发货单号 |
| 采购入库 · 已入库 | 采购入库单号；发货单号、快递单号；采购订单编号、店铺、供应商、采购订单型号 | `warehouse/service/ReceiptService:123` | 漏了供应商和店铺 |
| 销售出库 | 销售出库单号、销售订单编号、出库型号、国内快递单号、客户（显示名） | `logistics/service/OutboundService:659、952` | 漏了快递单号 |
| 退货入库 | 退货入库单号、订单编号、退货单号、退货快递单号、售后单号、型号 | `aftersales/service/ReturnReceiptService:449` | 无 |
| 闲置库存 | 型号、品牌；采购订单编号、店铺、供应商、采购订单型号；销售订单编号 | `warehouse/service/HoldService:65` | 漏了品牌 |
| 实拍任务 | 型号、品牌、采购入库单号、销售订单编号 | `warehouse/service/ShootService:139` | 无 |
| 出运管理 | 出运单号、运单号、销售订单编号、客户（显示名） | `logistics/service/LogisticsShipmentService:551、840` | 无 |
| 货代管理 | 名称、简称、联系人、联系电话 | `logistics/service/ForwarderService:69` | 漏了电话 |

## 财务

| 页面 | 可搜范围 | 代码位置 | 提示问题 |
|---|---|---|---|
| 应收管理 | 客户名称、订单编号、PI 编号（前端筛选，不区分大小写；develop v1.47.0 新增） | `zhul-erp-frontend/src/pages/finance/receivables/receivables.ts:27` | 漏了 PI 编号（7.4 已补） |
| 收款管理 · 待确认 | PI 编号（不带前缀也能搜到）、订单编号（只限收款记在订单上的手动订单和费用订单）、客户名称或简称 | `sales/service/impl/PaymentReceiptServiceImpl:650` | 漏了订单编号 |
| 收款管理 · 未认领到账 | 付款人、备注、金额（去掉千分位逗号后匹配） | `sales/service/impl/ReceiptLedgerServiceImpl:83` | 无 |
| 收款管理 · 收款单 | 收款单号、平台订单号、付款人、PI 编号、订单编号（只限手动订单和费用订单）、客户名称、简称或联系人 | `sales/service/impl/ReceiptLedgerServiceImpl:248` | 漏了联系人 |
| 退款管理 | 退款单号、订单编号、客户名称 | `finance/service/CustomerRefundService:527` | 无 |
| 退款申请 | 申请号、退货单号、订单编号、售后单号 | `finance/service/RefundRequestService:480` | 无 |
| 余额管理 | 客户显示名（不区分大小写） | `finance/service/CustomerCreditService:107` | 无 |
| 应付管理 | 应付单号、收款方、来源单号 | `finance/service/PayableQueryService:74` | 无 |
| 付款管理 | 付款单号、收款方 | `finance/service/PaymentService:675` | 无 |
| 报销管理 | 报销单号、提交人 | `finance/service/ReimbursementService:731` | 无 |
| 费用管理 | 费用编号、名称、收款方 | `finance/service/ExpenseService:680` | 无 |

## 商品与运营

| 页面 | 可搜范围 | 代码位置 | 提示问题 |
|---|---|---|---|
| 商品档案 | 商品名称、型号（含规范化）、订货号 | `product/service/impl/ProductServiceImpl:178` | 漏了订货号 |
| 商品候选 | 型号（含规范化）、品牌文字、商品名称、订货号 | `product/candidate/service/ProductCandidateService:148` | 漏了订货号 |
| 品牌管理 | 品牌名称、别名 | `product/service/impl/BrandServiceImpl:72` | 无 |
| 品类管理 | 品类编码、英文名、中文名（前端筛选，不区分大小写） | `product/category/index.tsx:414` | 提示写「编码或名称」，可以保留 |
| 系列管理 | 系列名称 | `product/service/impl/SeriesServiceImpl:63` | 无 |
| 资料任务 | 商品名称、型号（含规范化） | `product/content/repository/ProductContentTaskMapper:42` | 无 |
| 发品任务 | 商品名称、型号（含规范化） | `listing/repository/ListingTaskMapper:32` | 无 |
| 素材管理 | 品牌、型号 | `media/repository/MediaItemMapper:16` | 无 |

## 供应链（2026-11-22 任务 7.2 补核）

| 页面 | 可搜范围 | 代码位置 | 提示问题 |
|---|---|---|---|
| 供应链跟进 · 需求情报、供应覆盖 | 型号（规范化后包含匹配） | `supply/service/SupplyBoardService:460` | 无（提示「搜索型号」） |
| 供应链跟进 · 供应商开发 | 品牌名称、品类名称（不区分大小写） | `supply/service/SupplierDevService:148` | 无（提示「搜索品牌、品类」） |
| 覆盖矩阵 · 型号清单 | 型号（包含匹配，不规范化） | `supply/service/CoverageMatrixService:108` | 无 |

## 系统管理与租户（2026-11-22 任务 7.8 补核）

| 页面 | 可搜范围 | 代码位置 | 提示问题 |
|---|---|---|---|
| 用户管理 | 姓名 | `system/service/impl/UserServiceImpl:62` | 无 |
| 角色管理 | 角色名称（接口另支持角色编码，页面未用） | `system/service/impl/RoleServiceImpl:111` | 无 |
| 岗位管理 | 岗位名称 | `system/service/impl/PositionServiceImpl:40` | 无 |
| 部门管理 | 部门名称（前端过滤，不区分大小写） | `system/dept/index.tsx:77` | 无 |
| 菜单管理 | 菜单名称（前端过滤，不区分大小写） | `system/menu/index.tsx:117` | 无 |
| 字典管理 | 字典名称 | `system/service/impl/DictTypeServiceImpl:34` | 无 |
| 系统配置 | 配置键、配置名称（不区分大小写） | `system/service/impl/ConfigServiceImpl:73` | 无 |
| 国家档案 | 国家名称（不区分大小写） | `masterdata/customerprofile/service/CountryProfileService:79` | 无 |
| 操作日志 | 操作名称、操作人姓名（两个输入框） | `system/service/impl/LogServiceImpl:177` | 无 |
| 租户列表 | 租户名称 | `tenant/service/impl/TenantServiceImpl:83` | 无 |
| 套餐管理 | 套餐名称 | `tenant/service/impl/TenantPackageServiceImpl:50` | 无 |

## 落地情况（2026-11-22 任务 7.8）

上表「提示问题」一栏列出的问题都已在对应批次改完：提示只列实际能搜的字段，最多 3 项，其余写进说明图标。闲置库存页已下线，不再处理。弹窗里的选择器搜索不属于列表页，不在本表范围。
