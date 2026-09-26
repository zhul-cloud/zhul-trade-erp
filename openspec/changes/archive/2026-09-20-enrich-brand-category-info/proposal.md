## Why

品牌和品类缺少可以对外展示的介绍内容，独立站的品牌页、品类页没有文案可用；品牌的原产地是自由文本，同一个国家会出现 `Germany`、`德国`、`Deutschland` 几种写法；品牌主题色要手输 HEX，容易输错，也没有校验。这些都是录入质量问题，改动集中在品牌和品类两个主数据上。

## What Changes

- 品牌新增“品牌简介”字段
- 品牌的原产地改为从统一的国家/地区清单中下拉选择，后端校验取值，现有数据里的 `USA` 迁移为标准写法 `United States`
- 品牌主题色改为颜色选择器，后端校验 `#RRGGBB` 格式；新建品牌时表单默认预填一个随机色
- 品类新增“品类简介”字段（品类仍是全局的，简介属于品类本身）
- 前端：品牌页加简介、原产地下拉、颜色选择器；品类页加简介；品牌、品类列表展示简介的摘要
- 不改变品牌、品类、系列、商品之间的关系，品类不挂到品牌下，系列不挂到品类下（用户于 2026-09-20 决定暂不做，见 design.md）

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `product/brand`：品牌简介、原产地统一清单与下拉、主题色格式与随机默认
- `product/category`：品类简介

## Impact

- **数据库**：新增增量脚本 `sql/build/sql/schema_v1.2.1.sql`（不改动已发布的 `schema_v1.2.sql`）：`product_brand` 和 `product_category` 各加 `description` 列；脚本内含 `USA` → `United States` 的数据订正和回滚段
- **后端**（`modules/product`）：`BrandService`、`CategoryService` 及 `SaveBrandRequest`、`SaveCategoryRequest`、`BrandVO`、`CategoryVO`、`CategoryOptionVO` 等；新增国家清单资源、读取接口和校验
- **前端**（`pages/product`）：`brand`、`category` 两个页面和 `service.ts`
- **测试**：`BrandServiceImplTest`、`CategoryServiceImplTest`、`ProductMasterDataIntegrationTest`、`ProductApiContractTest` 需要补用例
- **文档与原型**：商品主数据 PRD、`.claude/context/data-model.md`、品牌和品类两页的 OpenPencil 原型
- **兼容性**：新增字段都是选填，现有接口调用方不受影响；原产地写入时若传入清单外的取值会被拒绝，这是行为收紧，`Taiwan, China` 等现有写法都在清单内
