## Context

现状（见 `sql/build/sql/schema_v1.2.sql`）：`product_brand` 有 `country`（varchar(64)，自由文本）和 `brand_color`（varchar(16)，HEX，无校验），没有简介；`product_category` 只有编码、名称、排序和状态，没有简介；`product_series` 已有简介（`description`）。库里 40 个品牌，其中 17 个有原产地：`Germany`、`France`、`Japan`、`USA`、`Switzerland`、`Taiwan, China`、`Denmark`。

品牌、品类、系列、商品之间的关系保持原样：品类是全局字典，系列挂在品牌下。用户于 2026-09-20 先后否决了“品类挂到品牌下”和“系列挂到品类下”两个方案，原因是暂时不需要这层关系。曾经评估过的“全局字典 + 品牌-品类关联表”方案不在本次范围内，以后需要时重新开变更。

## Goals / Non-Goals

**Goals:**
- 品牌和品类各有一段简介
- 原产地统一为清单下拉，服务端校验
- 主题色用颜色选择器录入，格式在服务端校验，新建时默认随机

**Non-Goals:**
- 不改品牌、品类、系列、商品之间的关系，不新增关联表
- 不做简介的多语言版本，不做独立站渲染
- 不把 `/Users/mac/Documents/llm-wiki/raw/assets/products/01-公司主营产品.pdf` 导入成数据，只作参考
- 不给原产地加“必填”限制，因为 40 个品牌里 23 个现在为空

## Decisions

### 1. 简介是两个新列，不新建表

`product_brand.description` 和 `product_category.description` 都是 `varchar(500) NOT NULL DEFAULT ''`，与 `product_series.description` 保持一致。简介随现有的读取接口返回（品牌和品类的列表 VO、选项 VO），写入走现有的新增、修改接口，长度在 Service 层校验，超长返回参数错误。

### 2. 国家清单：后端一份 JSON 资源，接口读取，写入时校验

放一份国家/地区清单资源文件（英文名、中文名、ISO 代码），后端启动时加载，`GET /api/v1/product/countries` 返回，前端下拉直接用这份数据，后端写入品牌时按英文名校验（比较时忽略大小写和首尾空格，落库时统一为清单里的写法）。保存的仍是英文名，与现有 `country` 列的数据形态一致，不用改列类型。清单遵循 ISO 3166 的英文简称，台湾、香港、澳门用 `Taiwan, China`、`Hong Kong, China`、`Macao, China` 的写法，与现有数据一致。

现有数据里的 `USA` 与 ISO 的简称 `United States` 不同，由增量脚本订正（涉及 Allen-Bradley、Honeywell 两个品牌），订正后所有现有取值都在清单内，所以编辑这些品牌时不会因为原产地被拒。

**备选**：用系统字典维护。字典按租户维护，而品牌是平台级共享数据，需要另做平台字典的读取；国家清单又几乎不变，不值得。**备选**：前端写死清单。后端就没法校验，接口调用方可以绕过。

读接口对所有登录用户开放，不需要新权限点。清单变化很少，前端每次打开品牌表单时读取一次即可，不加缓存。

### 3. 主题色：前端选择器加随机默认，后端只校验格式

前端用 antd `ColorPicker`（带预设色），新建时从一组预设的 16 个品牌友好色（在浅色、深色底上都可读）里随机取一个预填，用户可以改、也可以清空。后端只校验 `^#[0-9A-Fa-f]{6}$` 并统一为大写，不在缺省时自动补色：通过接口创建的品牌颜色为空也合法，与历史数据一致。

**备选**：由后端在缺省时随机分配。会让“没填”和“随机出来的”无法区分，也让接口行为难以预测。

### 4. 接口与权限

- 品牌：`SaveBrandRequest`、`BrandVO`、`BrandOptionVO` 增加 `description`；原产地和主题色按上面的规则校验
- 品类：`SaveCategoryRequest`、`CategoryVO`、`CategoryOptionVO` 增加 `description`
- 新增 `GET /api/v1/product/countries`
- 写权限继续沿用 `product:brand:*`、`product:category:*` 和 `PlatformScopeGuard`，不新增权限点，也不用重新给角色授权

### 5. 前端

- **品牌页**：表单加品牌简介（多行，带字数提示）、原产地（可搜索下拉，选项显示“中文名 英文名”）、颜色选择器；列表展示原产地和主题色小圆点
- **品类页**：表单加品类简介；列表展示简介摘要（单行省略，悬停看全文）

### 6. 迁移

新增增量脚本 `sql/build/sql/schema_v1.2.1.sql`（不改已发布的 `schema_v1.2.sql`）：加两个 `description` 列，订正 `USA`。MySQL 8 的 `ADD COLUMN` 不支持 `IF NOT EXISTS`，脚本用 `information_schema` 判断后用预处理语句执行，保证可重复执行。测试库重置脚本同步加载它。回滚段删除两个新列并把 `United States` 还原为 `USA`。

## Risks / Trade-offs

- [原产地写入变严，调用方传清单外的取值会被拒] → 清单包含现有全部取值，迁移后编辑现有品牌不受影响；接口变化写入 PRD
- [清单需要跟着 ISO 更新] → 清单放资源文件，改文件即可，不涉及库
- [随机色可能撞色或与背景对比度不足] → 只从预设的 16 个色里取，预设已按浅色和深色底检查过；用户可随时更改
- [回滚会丢失已录入的简介] → 回滚前先备份

## Migration Plan

1. 备份 `zhul_erp`，先在 `zhul_erp_test` 演练
2. 执行 `schema_v1.2.1.sql`，核对 `SELECT DISTINCT country FROM product_brand` 的结果全部在清单内
3. 先部署后端，再部署前端（前端依赖新接口和新字段）
4. 回滚：前后端回退到上一版本；旧版本不读新列，也可以只回退代码而保留新列
