## Why

询盘明细里的品牌、品类、型号目前都是自由文本（`inquiry_order_item.brand` / `category` / `original_model` / `confirmed_model`），同一个商品会有多种写法（如 `6ES7 214-1BD23-0XB0` 与 `6ES7214-1BD23-0XB0`），无法跨询盘、跨供应商、跨订单聚合，后续报价、采购、订单也没有可引用的统一商品标识。`.claude/context/data-model.md` 已把 v1.2 商品域列为下一步，独立站 `fouwell-website/js/data.js` 里已有 87 条现成商品数据（40 个品牌、6 个品类）可作为首批数据，`llm-wiki` 中也已有一份 v1.2.0 草案 DDL 可以对照修正。

## What Changes

- 新增商品域十三张表：`product_brand`、`product_category`、`product_series`、`product`、`product_specification`、`product_relationship`，以及内容类的 `product_document`（技术资料）、`product_application`（应用场景）、`product_faq`，以及 `product_media`（图片与视频）、`product_logistics`（物流）、`product_customs`（海关）、`product_reference_price`（平台参考价），追加为 `sql/build/sql/schema_v1.2.sql`，**不修改任何 v1.0 / v1.1 已有表**
- **商品单层**：一个「品牌 + 型号」= 一个商品，不设 SPU/SKU；系列只是品牌下的可选分组
- **平台共享**：所有租户共用一份商品库，数据行 `tenant_id` 固定为 0（沿用项目已有的"0=平台级共享"约定）；写接口要求权限码 **且** 平台账号，租户账号只读
- **型号去重**：同一品牌下按归一化型号（NFKC、小写、去掉所有非字母数字）唯一，空格 / 连字符 / 大小写不同的同一型号不能并存
- **引用保护**：被业务单据引用的商品不能删除、不能改品牌和型号，只能停用；已软删除的商品不能重新新建同型号，只能恢复
- 商品**选择器搜索接口**与**型号匹配接口**，供询盘等业务模块选择、关联商品
- **新建向导与档案完整度**：新建商品改为 5 步向导（型号 → 分类 → 图片 → 补充资料 → 完成），只有前 2 步必填，第 2 步结束时创建商品，第 1、2 步草稿存浏览器本地；每个商品有 10 个模块的档案完整度（计算值，仅平台账号可见），列表提供缺图片 / 缺物流 / 缺海关 / 缺参考价快捷筛选
- **图片与视频**：平台账号可上传（图片 ≤5MB、视频 ≤100MB，校验扩展名与文件头、禁 SVG）或登记外链；每个商品一张主图
- **物流与海关**：重量、尺寸、包装、危险品标记；HS 编码（一个商品一个）、申报品名、默认原产国、出口退税率
- **平台参考价**：平台共享的参考价（原币 + 币种 + 汇率 + 本位币），不是任何租户的报价或售价；租户各自的采购价和售价仍不放进共享表
- **技术资料、应用场景、FAQ**：挂在商品下、平台共享；FAQ 有发布门禁——待审核内容只有平台账号能读到，审核确认后对所有租户可见；文件地址只存地址并校验协议
- 型号关系（官方替代 / 厂商后续型号 / 功能性替代 / 兼容 / 交叉引用 / 同系列）及置信度，对称类型支持同时创建反向关系
- 前端：品牌、品类、系列、商品四类管理页，商品新建 5 步向导、商品档案单页（卡片式，含档案完整度与待补清单），`ProductSelect` 选择器组件
- 一次性迁移脚本：读取独立站 `js/data.js`，先干跑再导入（87 个商品、40 个品牌、6 个品类、80 个系列、1 份技术资料、117 条应用场景、222 条 FAQ、56 条外部参考价）；FAQ 中夹带卖家质保、库存、联系方式承诺的条目不导入；福唯自己的 14 条采购报价和 52 张实拍图不导入
- **不做**（已明确排除，见 design.md）：各租户自己的采购价 / 售价 / 库存 / 成色 / MOQ / 交期等租户级商业数据；SEO 发布字段；卖家自己的质保 / 库存 / 发货 / 联系方式类 FAQ（不进平台共享表）；技术资料文件的上传（图片视频可上传）；询盘明细关联商品的改造；独立站切换到 ERP 数据源

## Capabilities

### New Capabilities
- `product/brand`: 品牌主数据管理（增删改查、启停、是否原厂正品标记、被商品使用时禁止删除）
- `product/category`: 品类主数据管理（沿用独立站 6 个品类编码，编码在有商品后不可修改）
- `product/series`: 系列管理（品牌下的可选分组，同品牌内名称唯一）
- `product/product`: 商品主数据管理（型号归一化去重、生命周期、启停、软删除与恢复、引用保护、规格参数、型号关系、技术资料、应用场景、FAQ 与发布审核、图片视频与上传、物流、海关、平台参考价、平台共享写权限）
- `product/product-lookup`: 商品选择器搜索与型号匹配（供询盘等业务模块选择、关联商品，只读）

### Modified Capabilities
（无——本次不修改任何既有能力的行为。询盘明细关联商品需要给 `inquiry_order_item` 加 `product_id`，将在后续独立的 change 中处理）

## Impact

- **数据库**：新增 `sql/build/sql/schema_v1.2.sql`（十三张表）；`resource` 表新增商品域菜单与按钮资源（`type=3`，权限码如 `product:brand:add`）
- **后端**：新增 `com.zhul.erp.modules.product` 模块（controller / service / repository / dto / entity / constants / support）；新增 `PlatformScopeGuard` 与 `ProductUsageChecker` 扩展点；新增商品自己的媒体存储接口（落本地磁盘 `zhul.upload.dir/product`，可迁 OSS）与上传限流、multipart 上限配置；品牌 / 品类 options 走 Redis 缓存
- **前端**：新增 `zhul-erp-frontend/src/pages/product/*`（列表、5 步向导、档案页）、`ProductSelect` 与档案完整度组件、路由与菜单；页面按 V3 方案 D 实现（信任蓝 + 渐变，深色默认、浅色并存，品牌主色调整为 `#2563EB`）。**全站外壳与主题令牌升级需另立 change**，本 change 不含，升级前商品页沿用现有外壳
- **文档**：更新 `.claude/context/data-model.md`、`docs/README.md`；PRD 落入 `docs/02-产品PRD/02-商品域/`；`llm-wiki` 中的 `Product-Master数据层架构决策.md` 需同步更新（仓库外）
- **后续依赖**：`link-inquiry-to-product`（给 `inquiry_order_item` 加 `product_id` 并实现 `ProductUsageChecker`）、「租户商品扩展」（价格 / 库存等，也是卖家类 FAQ 的去处）、独立站 `ErpProductRepository` 对接
- **已确认的前提**（2026-09-19）：新建改为 5 步向导、详情改为单页卡片并引入档案完整度；采用 V3 方案 D（信任蓝 + 渐变，深色默认、浅色并存），全站外壳与主题令牌升级另立 change；平台账号 JWT 的 `tenantId` 为 0；各租户自己的价格、库存移出商品主表；商品主数据包含图片视频（共享、可上传）、物流、海关（HS 编码一个商品一个）和平台共享参考价
