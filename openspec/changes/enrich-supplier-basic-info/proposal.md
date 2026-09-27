## Why

供应商管理页（`add-customer-supplier-management` 刚上线）只有名称、国家、联系人、电话、邮箱、主营品牌几个字段，用弹窗编辑。新定稿的原型（`docs/03-产品原型/01-主数据域/00-主数据中心/02-供应商管理/00-供应商基础信息/`）和 PRD（`docs/02-产品PRD/01-主数据域/00-主数据中心/02-供应商管理/00-供应商基础信息/供应商基础信息管理-PRD-V1.0.md`）要求供应商主数据覆盖编码、工商信息、地区地址和结算账户，才能给后续的采购、结算提供权威的供应商信息来源。

## What Changes

- 供应商表新增字段：供应商编码、简称、供应商类型、所属行业、统一社会信用代码、法人代表、注册资本（万元人民币）、成立日期、所在地区、详细地址、开户银行、银行账号、备注
- 供应商编码：页面新增时必须手工填写（字母数字、≤20 位、租户内唯一），创建后不可修改；询盘等场景的内联快速创建不填编码时由系统自动生成（`SUP` + 5 位补零主键）；存量数据由迁移脚本补齐编码
- 统一社会信用代码：选填，填了就要符合 18 位格式，并在租户内唯一，冲突时提示被哪个供应商占用
- 银行账号：只允许数字；列表、详情、导出按"前 4 位 + `**** ****` + 后 4 位"脱敏，编辑页明文回显（单独的编辑取数接口，需要编辑权限）
- 列表页按原型重做：筛选（编码、名称、信用代码、类型、状态）、行首复选框 + 批量删除、状态开关（禁用前二次确认）、查看/编辑/删除、导出 Excel、导入按钮置灰占位
- 新增、编辑、详情从弹窗改成独立页面，四张分组卡片（基本信息、工商信息、联系信息、结算信息），详情页多一张系统信息卡片
- 新增批量删除接口、导出接口、编辑取数接口
- 新增省市区三级行政区划查询接口，数据以 JSON 文件放在后端，供「所在地区」级联选择器使用
- 保留现有接口的兼容：内联创建（`POST /suppliers`）、选择器搜索（`GET /suppliers`）的入参出参只增不减，`country`、`main_brands` 字段保留在库里和接口里，只是新页面不再展示
- 菜单不动，仍挂在「客商管理 → 供应商管理」（`/partner/suppliers`）

**不在本次范围内**：
- 导入（模板下载、逐行校验、失败明细），按钮先置灰，下一期单独做
- 删除前校验"已有采购业务关联"：采购模块还没有，本次删除仍然是直接软删除（沿用已有语义：历史询盘/报价引用不受影响）
- 按原型把菜单重组为「主数据中心」、供应商等级、供应商分类页面

## Capabilities

### New Capabilities
- `master-data/region`：中国省市区三级行政区划查询，供地址类字段的级联选择使用

### Modified Capabilities
- `master-data/supplier`：新增供应商编码、工商信息、地区地址、结算信息等字段及其校验规则；新增独立的详情和编辑取数、批量删除、导出、按新条件分页筛选。（该能力的既有需求目前还在未归档的 `add-inquiry-management`、`add-customer-supplier-management` 两个变更里，主规格目录下尚无文件，所以本次以 ADDED 形式追加需求）

## Impact

- **数据库**：新迁移 `V1.2.8__supplier_basic_info.sql`，`supplier` 表加 13 个字段和索引，存量记录补齐编码
- **后端**：`masterdata` 模块的 `SupplierDO`、`SupplierVO`、`SaveSupplierRequest`、`UpdateSupplierRequest`、`SupplierPageQuery`、`SupplierService(Impl)`、`SupplierController` 扩展；新增供应商类型/行业常量、银行账号脱敏工具、Excel 导出、区划数据加载组件和接口；新增按钮权限 `partner:supplier:export`
- **前端**：`src/pages/partner/supplier/` 重写为列表页 + `form.tsx`（新增/编辑共用）+ `detail.tsx`；`config/routes.ts` 增加 `/partner/suppliers/new`、`/partner/suppliers/:id`、`/partner/suppliers/:id/edit` 三个隐藏路由；`access.ts` 增加导出权限位；`src/services/zhul/masterdata.ts` 的 `SupplierItem` 类型补字段（询盘侧调用方不受影响）
- **不影响**：询盘单/报价对供应商的引用（只存 `supplier_id`）、内联快速创建流程
