## 1. 文档与原型

- [x] 1.1 更新商品主数据 PRD（V1.1 已被交互改版占用，本次记为 V1.2，文件名不变）：品牌加简介、原产地下拉与清单、主题色选择器与随机默认；品类加简介；修订记录写明本次变更，验证：PRD 的品牌、品类两节与 `specs/product/brand`、`specs/product/category` 的场景逐条对得上
- [x] 1.2 更新 `.claude/context/data-model.md`：`product_brand.description`、`product_category.description`，验证：字段与 design.md、`schema_v1.2.1.sql` 一致
- [x] 1.3 用 OpenPencil 更新品牌、品类两页原型（深色为主，遵循 `ui-design-patterns.md` 的消费级质感要求）：品牌表单含简介、原产地下拉（含搜索、空态）、颜色选择器（含随机默认、清空）；品类表单含简介；两个列表展示简介摘要，验证：`.op` 文件已保存到 `docs/03-产品原型/02-商品域/00-商品主数据/`，导出截图人工检查空态、错误态、超长文本都已画出（只提交 `.op`，不提交 PNG）

## 2. 数据库

- [x] 2.1 新增 `sql/build/sql/schema_v1.2.1.sql`（不修改 `schema_v1.2.sql`）：`product_brand` 和 `product_category` 各加 `description varchar(500) NOT NULL DEFAULT ''`（注释写明含义）；订正 `product_brand.country` 里的 `USA` 为 `United States`；文件末尾附回滚段，验证：在已执行 v1.2 的库上重复执行两次无报错，`SHOW CREATE TABLE` 与 design.md 一致，`git diff` 确认 `schema_v1.2.sql` 未被修改
- [x] 2.2 更新 `sql/build/test/reset-test-db.sh`，在 `schema_v1.2.sql` 之后加载 `schema_v1.2.1.sql`，验证：重置后 `zhul_erp_test` 里两张表都有 `description` 列

## 3. 后端

- [x] 3.1 国家清单：新增清单资源文件（ISO 3166，英文名、中文名、代码；台湾、香港、澳门用 `Taiwan, China` 这类写法）、加载与校验组件，`GET /api/v1/product/countries`，验证：单元测试覆盖清单加载、英文名校验通过（忽略大小写和首尾空格）、清单外取值被拒绝，且开发库里现有的全部原产地取值（订正后）都在清单内
- [x] 3.2 品牌：请求、VO 和选项 VO 增加 `description`（≤500）；原产地写入时按清单校验并落库为清单写法；主题色校验 `#RRGGBB` 并统一为大写，验证：单元测试覆盖 `specs/product/brand/spec.md` 的全部场景（简介填写与超长、不填简介、选择清单内原产地、清单外原产地被拒、不填原产地、色值 `red` 和 `#12345` 被拒、`#e60012` 存为大写）
- [x] 3.3 品类：请求、VO 和选项 VO 增加 `description`（≤500），验证：单元测试覆盖 `specs/product/category/spec.md` 的全部场景，含“已有商品使用的品类仍可修改简介且编码不变”
- [x] 3.4 品牌和品类选项的 Redis 缓存：确认简介修改后缓存被清除（沿用现有的事务提交后删除），验证：集成测试确认修改简介后 `options` 返回新简介
- [x] 3.5 补充和更新既有测试：`BrandServiceImplTest`、`CategoryServiceImplTest`、`ProductMasterDataIntegrationTest`、`ProductApiContractTest`；契约测试覆盖 `countries` 的响应结构与品牌、品类新增字段，验证：`mvn test` 全部通过，租户账号写品牌、品类仍被拒绝

## 4. 前端

- [x] 4.1 `pages/product/service.ts` 与常量：新增国家清单接口封装，品牌、品类类型增加 `description`，新增 16 个预设品牌色与随机取色函数，验证：`tsc` 无新增错误，随机取色函数有单元测试（结果总在预设内、连续多次调用出现不同值）
- [x] 4.2 品牌页：表单加品牌简介（多行，字数提示）、原产地可搜索下拉、颜色选择器（新建默认随机色，可清空）；列表展示原产地和主题色，验证：浏览器实测新建时色值已预填、选国家后保存成功、简介超长有内联错误提示，深色和浅色截图检查
- [x] 4.3 品类页：表单加品类简介；列表展示简介摘要，验证：浏览器实测保存、超长提示、空态
- [x] 4.4 前端质量：`biome`、`tsc`、`npx antd lint ./src` 对本次改动的文件无新增问题，品牌和品类两页用 axe 扫描深色和浅色无颜色对比度和名称缺失问题，验证：命令输出和扫描结果为空

## 5. 迁移与收尾

- [x] 5.1 在开发库执行迁移：先备份，再执行 `schema_v1.2.1.sql`，验证：两张表都有新列，`SELECT DISTINCT country FROM product_brand` 的结果全部在国家清单内，没有 `USA`
- [x] 5.2 端到端验收：平台账号新建品牌（下拉原产地、随机色、简介）、编辑品类简介，租户账号确认只读，验证：流程通过，租户账号写操作被拒
- [x] 5.3 更新 `docs/README.md` 中商品域的状态与 PRD 版本索引，归档该变更并同步主 specs，验证：`openspec validate --specs` 通过，`openspec/specs/product/brand`、`category` 已包含本次的新增
