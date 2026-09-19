## 1. 前置确认

- [x] 1.1 确认平台账号登录后 JWT 的 `tenantId` 是否为 0（design.md Q2）：**用户于 2026-09-19 确认为 0**，已写回 design.md（决策 2 的前提、Open Questions 中 Q2 已划掉）。注：仓库内无账号种子数据，未在代码中独立核实，任务 3.3 的测试需固化这一约定
- [x] 1.2 与用户确认决策 3（商业数据与站点发布字段移出商品主表）：**用户于 2026-09-19 确认同意**，已写回 design.md（决策 3）。design.md 中 Q1 仅剩"租户商品扩展放哪个版本"，不影响本 change

## 2. 数据库

- [ ] 2.1 新增 `sql/build/sql/schema_v1.2.sql`，按 design.md 决策 11 建 `product_brand`、`product_category`、`product_series`、`product`、`product_specification`、`product_relationship`、`product_document`、`product_application`、`product_faq` 九张表，验证：在已执行 `schema_v1.sql`、`schema_v1.1.sql` 的本地 MySQL 8 上执行无报错、可重复执行；`SHOW CREATE TABLE` 与 design.md 一致；`git diff` 确认 v1.0 / v1.1 的 SQL 文件未被修改
- [ ] 2.2 验证唯一键的大小写行为：向 `product_brand` 依次插入 `Siemens`、`siemens`，向 `product_series` 在同一品牌下依次插入 `S7-1200`、`s7-1200`，验证：第二次插入均报唯一键冲突（对应 `specs/product/brand/spec.md`、`specs/product/series/spec.md` 的"忽略大小写"）；若不冲突，给 `brand_name`、`series_name` 显式指定不区分大小写的排序规则并更新 design.md 的 DDL
- [ ] 2.3 在 `resource` 表新增商品域菜单与按钮资源（`type=3`，权限码见 design.md 决策 2 的表），种子数据写法对照现有 `resource` 数据，验证：给某角色分配 `product:brand:add` 后 `PermissionChecker.has("product:brand:add")` 返回 true，未分配返回 false（集成测试）
- [ ] 2.4 更新 `.claude/context/data-model.md`（v1.2 表清单、"tenant_id=0 平台共享"说明，并把 v1.2 从"后续版本规划"移入已落地）与 `docs/README.md`（v1.2.0 状态与 PRD 索引），验证：表清单与 `schema_v1.2.sql` 一致

## 3. 后端 — 基础（modules/product）

- [ ] 3.1 建立 `com.zhul.erp.modules.product` 模块骨架（controller / service / repository / dto / entity / constants / support），九张表的 Entity(DO) 与 Mapper，`LifecycleStatus`、`RelationshipType`、`RelationshipConfidence` 常量类与错误码（design.md 决策 10），验证：项目编译通过，常量取值与 design.md 一致（单元测试）
- [ ] 3.2 实现 `MpnNormalizer`（NFKC → 小写 → 去掉所有非字母数字，design.md 决策 4），验证：单元测试覆盖 `6ES7 214-1BD23-0XB0`、`6ES7214-1BD23-0XB0`、首尾空格、全角 `６ＥＳ７２１４`、`SGMAH-04ADA-TF13`、`---`（空串）、`null`，覆盖率 100%
- [ ] 3.3 实现 `PlatformScopeGuard`（要求 `TenantContext.getTenantId() == 0`），以及写接口统一使用的"权限码 + 平台账号"校验方式（design.md 决策 2），验证：单元测试覆盖 tenantId 为 0 通过、tenantId 为 1001 拒绝并返回 `PLATFORM_ADMIN_REQUIRED`、`TenantContext` 为空拒绝
- [ ] 3.4 实现商品模块统一的租户处理：所有查询恒带 `tenant_id = 0`，写入时强制写 0 并忽略客户端传值（design.md 决策 1），验证：单元测试确认请求体带 `tenantId=1001` 时落库仍为 0；租户 1001 的登录用户读取到的数据与租户 1002 一致（对应 `specs/product/product/spec.md` "不同租户读到同一份商品"）

## 4. 后端 — 品牌 / 品类 / 系列

- [ ] 4.1 实现品牌的创建、修改、启停、列表、`options`：名称必填且 ≤64、平台内唯一（忽略大小写与首尾空格）、`is_genuine` 默认 1，验证：单元测试覆盖 `specs/product/brand/spec.md` 的创建、重复（`siemens ` vs `Siemens`）、空名称、标记兼容品牌、停用后不出现在 options 的场景
- [ ] 4.2 实现品牌删除保护：下面仍有未删除商品时拒绝并返回使用数量，否则软删除，验证：单元测试覆盖"有 5 个商品时拒绝且提示 5"与"无商品时软删除后正常查询不再返回"
- [ ] 4.3 实现品类的创建、修改、启停、列表、`options`：编码格式 `^[a-z][a-z0-9_]*$` 且 ≤32、平台内唯一、有商品后编码不可修改（名称可改）、被商品使用时不可删除，验证：单元测试覆盖 `specs/product/category/spec.md` 的全部场景（含 `PLC Controllers` 格式非法、`drives` 有商品后改编码被拒、改名称成功）
- [ ] 4.4 实现系列的创建、修改、启停、列表、`options`（支持 `brandId`）：归属已存在品牌、同品牌内名称唯一、不同品牌允许同名、有商品时不可删除，验证：单元测试覆盖 `specs/product/series/spec.md` 的全部场景
- [ ] 4.5 实现品牌与品类 `options` 的 Redis 缓存（`zhul:erp:list:0:product_brand`、`zhul:erp:list:0:product_category`，TTL 300s，Cache-Aside），写库事务**提交后**再删缓存（`TransactionSynchronization.afterCommit`，design.md 决策 9），验证：集成测试确认修改品牌后缓存被清除；事务回滚时缓存不被清除
- [ ] 4.6 给品牌 / 品类 / 系列的所有写接口加 `@PreAuthorize("@perm.has('product:...')")` 并接入 `PlatformScopeGuard`，验证：三类资源各有测试覆盖 `specs` 中"租户账号即使被分配权限仍被拒绝"场景，读接口对任意租户登录用户开放

## 5. 后端 — 商品（specs/product/product/spec.md）

- [ ] 5.1 实现创建商品：品牌 / 品类 / 原始型号必填，`mpn_raw` 仅去首尾空格，`mpn_display` 默认等于 `mpn_raw`，系列须属于该品牌，生命周期默认 6，验证：单元测试覆盖"创建商品"与"系列不属于所选品牌被拒绝"场景
- [ ] 5.2 实现归一化去重：命中同品牌未删除商品返回 `PRODUCT_DUPLICATE`（`detail.existingId`、`deleted=false`），命中已软删除商品返回 `deleted=true`，不同品牌允许相同型号，归一化为空返回 `PRODUCT_MPN_INVALID`，捕获 `DuplicateKeyException` 转 `PRODUCT_DUPLICATE`，验证：单元测试覆盖空格 / 大小写连字符 / 全角 / 不同品牌 / `---` 五个场景；集成测试用两个线程同时创建同一型号，断言只成功一个
- [ ] 5.3 实现 `POST /products/{id}/restore`：恢复后 ID、规格、型号关系、技术资料、应用场景、FAQ 与删除前一致，验证：集成测试覆盖"新建命中已删除商品被拒绝"与"恢复已删除商品"
- [ ] 5.4 实现生命周期规则：4 / 5 必须有 `lifecycle_source`（`PRODUCT_LIFECYCLE_SOURCE_REQUIRED`）；生命周期为 5 且存在类型 1/2/3 的关系时允许保存并在响应的 `warnings` 数组中给出提示，验证：单元测试覆盖 `specs/product/product/spec.md` 生命周期的三个场景
- [ ] 5.5 实现商品启停：停用后不出现在选择器与匹配结果，已引用的历史单据不受影响，验证：单元测试覆盖"停用被引用的商品"（使用一个返回引用次数大于 0 的测试用 `ProductUsageChecker`）
- [ ] 5.6 实现 `ProductUsageChecker` 扩展点与删除保护：`SELECT ... FOR UPDATE` 锁商品行 → 汇总所有 checker 的次数 → 大于 0 返回 `PRODUCT_IN_USE`（`detail.usageCount`），否则软删除；被引用后修改品牌或原始型号返回 `PRODUCT_MPN_IMMUTABLE`，其他字段可改（design.md 决策 6、9），验证：单元测试覆盖删除被引用 / 未引用、改型号被拒、改产品名称成功四个场景
- [ ] 5.7 实现规格参数的读取与整体替换（`GET/PUT /products/{id}/specifications`），同一事务内先软删旧行再插入新行，同一集合内规格编码重复时整体拒绝且原规格不变，验证：集成测试覆盖"整体替换（3 条换成 2 条）"与"编码重复被拒且原规格不变"
- [ ] 5.8 实现型号关系的增改删：关系类型与置信度枚举校验、不能关联自己、同商品下（关联型号归一化值 + 类型）不可重复、置信度为"已验证"时 `verified_by` 必填并由服务端填 `verified_at`、关联型号只命中唯一目录商品时自动填 `related_product_id` 而命中多个品牌时不填，验证：单元测试覆盖 `specs/product/product/spec.md` 型号关系的全部六个场景
- [ ] 5.9 实现对称类型反向关系：类型 3/4/5/6 且 `createReverse=true` 时同一事务再写反向关系（两端都须是目录内商品），类型 1/2 忽略该参数，反向失败时整体回滚，验证：集成测试覆盖"对称类型创建反向关系""非对称类型不生成反向关系""反向创建失败整体回滚"
- [ ] 5.10 实现商品分页列表与详情：列表支持 `keyword`、`brandId`、`categoryId`、`seriesId`、`lifecycleStatus`、`status`，`includeDeleted=true` 仅平台账号有效，详情含 `usageCount`，验证：单元测试覆盖筛选组合，租户账号传 `includeDeleted=true` 不返回已删除商品
- [ ] 5.11 给商品、规格、型号关系、技术资料、应用场景、FAQ（含 approve）的所有写接口加 `@PreAuthorize` 与 `PlatformScopeGuard`，验证：测试覆盖 `specs/product/product/spec.md` 最后一条需求的四个场景（租户账号被拒、无权限码的平台账号被拒、`adminFlag=1` 的平台账号通过、不同租户读到同一份数据）

- [ ] 5.12 实现技术资料的增删改与列表（`/products/{id}/documents`）：类型枚举校验、标题与文件地址必填、文件地址只允许 `http://`、`https://` 或单个 `/` 开头（拒绝 `javascript:`、`data:`、`//host`，返回 `DOCUMENT_URL_INVALID`）、同商品下未删除行内文件地址不可重复（`CONTENT_DUPLICATE`）、`verified=1` 时服务端填 `verified_at`，验证：单元测试覆盖 `specs/product/product/spec.md`「商品技术资料」的六个场景，地址校验的边界值（空串、大小写协议 `HTTPS://`、`/`、`//x`）另加用例
- [ ] 5.13 实现应用场景的增删改与列表（`/products/{id}/applications`）：标题必填且 ≤64、说明 ≤500、同商品下标题忽略大小写与首尾空格不可重复，验证：单元测试覆盖「商品应用场景」的三个场景
- [ ] 5.14 实现 FAQ 的增删改、列表与审核（`/products/{id}/faqs`、`PATCH .../approve`）：管理员新增恒为 `source=2`、修改内容不改 `source`、`approve` 只接受 `source=3`（否则 `FAQ_NOT_PENDING`）并写入 `reviewed_by`/`reviewed_at`、**所有返回 FAQ 的读取路径对租户账号在服务端过滤 `source=3`**（列表、单条），验证：单元测试覆盖「商品 FAQ 与发布审核」的六个场景；集成测试用 `tenantId=1001` 的登录用户确认读不到 `source=3` 行、平台账号能读到，审核后租户账号可读到

## 6. 后端 — 商品查找（specs/product/product-lookup/spec.md）

- [ ] 6.1 实现 `GET /products/search`：关键词归一化后走 `idx_mpn_normalized` 前缀匹配，同时按产品名称 `LIKE` 匹配（转义 `%`、`_`）；**归一化后为空时跳过型号前缀分支**；只返回启用且未删除的商品与精简字段；`limit` 缺省 20、最大 50、非正数按缺省；空关键词返回空列表，验证：单元测试覆盖该 spec 选择器搜索的五个场景（空格与大小写不影响命中、只返回启用未删除、结果上限、空关键词、按产品名称匹配），另加一条"关键词为 `---` 时不返回全部商品"
- [ ] 6.2 实现 `GET /products/match`：品牌按忽略大小写与首尾空格比较，返回 `exact`（同品牌同归一化型号）与 `candidates`（其他品牌同归一化型号 + 前缀匹配，最多 10 条），只返回启用且未删除的商品，验证：单元测试覆盖该 spec 型号匹配的五个场景
- [ ] 6.3 汇总契约测试与覆盖率：所有接口响应符合 `{"code":0,"data":{},"message":"ok"}`，错误响应含 `code`/`message`/`detail`，验证：整体语句覆盖率 ≥70%，`MpnNormalizer`、生命周期规则、型号关系规则、FAQ 发布门禁与审核覆盖率 100%，`openspec validate add-product-master-core --strict` 通过

## 7. 前端（zhul-erp-frontend/src/pages/product/）

- [ ] 7.1 用 OpenPencil 出商品域原型（品牌 / 品类 / 系列 / 商品列表、新建商品弹窗、商品详情六个 Tab（基本信息 / 规格参数 / 型号关系 / 技术资料 / 应用场景 / FAQ）、型号关系弹窗），存入 `docs/03-产品原型/02-商品域/`，结构与 Design Token 遵循 `.claude/context/ui-design-patterns.md`，验证：`.op` 文件与效果图已提交并经用户确认；**7.2 起的页面任务以此为准**。**进度（2026-09-19）**：已出稿 `docs/03-产品原型/02-商品域/00-商品主数据/商品主数据.op`，23 个画板及 PNG，每个画板旁有交互标注；待用户确认
- [ ] 7.2 实现品牌、品类、系列三个列表页与新增/编辑弹窗（ProTable / ProForm）与各自的 `service.ts`：被使用的记录删除按钮置灰并提示使用数量，写操作按钮按权限码隐藏，验证：类型检查与 lint 通过，手动走查新增、重名提示、停用、删除被使用记录被拒
- [ ] 7.3 实现商品列表页与新建商品弹窗：型号输入框失焦调用 `match`，命中未删除商品时提示并给出链接、命中已删除商品时给出"恢复"入口，验证：类型检查与 lint 通过，手动输入带空格与不带空格的同一型号，提示一致（对应 PRD 5.1）
- [ ] 7.4 实现商品详情页：基本信息、规格参数（可编辑表格，整体保存）、技术资料 / 应用场景 / FAQ 三个 Tab（逐条增删改；FAQ 待审核行显示"待审核"标签与"确认"按钮；文件地址输入框做协议前置提示，服务端校验为准）、型号关系 Tab 与添加弹窗（仅对称类型显示"同时创建反向关系"，置信度选"已验证"时必填核实人），已被引用时品牌与型号只读并提示引用数量，验证：类型检查与 lint 通过，手动走查 PRD 5.2、5.3 流程
- [ ] 7.5 实现 `ProductSelect` 选择器组件（远程搜索、输入防抖、只显示启用商品、展示"展示型号 · 品牌 · 品类"），验证：类型检查与 lint 通过，在商品列表页之外的任一页面引用该组件可正常搜索选择
- [ ] 7.6 接入路由与菜单，验证：菜单与 2.3 的 `resource` 数据一致，租户账号登录后只看到只读入口，写按钮不可见

## 8. 数据迁移（一次性）

- [ ] 8.1 检视 `js/data.js` 中 `compatibility` 的子字段结构（design.md 迁移表中仍标为"未检视"），把结论补进 design.md 迁移映射，验证：迁移映射表中 `compatibility` 一行不再含"未检视"。已知（2026-09-19 实测）：`datasheet`、`applications`、`faq` 的结构已检视并写入迁移表；`compatibility` 为 12 个商品共 22 条 `{from, type?, note}`，其中 7 个商品带 `type`（如 `direct`/`compatible`/`functional`），5 个商品没有 `type`，`type` 到关系类型的映射待定
- [ ] 8.2 编写迁移脚本（Node，放入 `scripts/product-migration/`）的**干跑模式**：读取 `fouwell-website/js/data.js`，只输出统计与异常，验证：输出商品 87 / 品牌 40 / 品类 6 / 系列 80，并报告同品牌下归一化型号碰撞、品牌名近似重复、空型号、缺品类，结果与 2026-09-19 的预干跑一致（均为 0 项异常）；另输出技术资料 1 条、应用场景 117 条（84 个商品）、FAQ 222 条（84 个商品），以及**疑似卖家承诺的 FAQ 完整清单与命中关键词**（design.md 迁移表）和技术资料文件是否存在（预期不存在）；脚本**不写库**
- [ ] 8.3 实现导入模式：生成幂等 SQL（商品主体用 `INSERT ... ON DUPLICATE KEY UPDATE`，内容类三张表用 `NOT EXISTS`），所有行 `create_by='migration'`；被排除的卖家类 FAQ 不生成 SQL，误伤条目通过人工放行清单加回，同时生成回滚 SQL，验证：空库导入后各表计数与 8.2 一致（FAQ = 222 − 排除数，全部 `source=3`）；重复执行一次计数不变；执行回滚后所有迁移行 `deleted_at` 非空
- [ ] 8.4 抽查迁移结果，验证：随机抽 5 个商品，其规格参数逐项与独立站对应页面一致；生命周期映射符合 design.md（`instock`→1、`legacy`→3、`discont`→4、带 `no_known_replacement` 的 1 条→5）；`sell_price*` 等字段未被迁入；导入的 FAQ 中没有任何一条含 `Fouwell`，随机抽 5 条 FAQ 与独立站页面一致，应用场景的 `icon` 保持 emoji 原样

## 9. 文档与收尾

- [x] 9.1 将 PRD 落入 `docs/02-产品PRD/02-商品域/00-商品主数据/商品主数据-PRD-V1.0.md`（仿照 `03-业务域/00-询盘中心/00-询盘单/` 的层级；`02-商品域` 的编号按 v1.2 在版本规划中的顺序推断，用户于 2026-09-19 认可）。来源为 `~/Documents/zhul-metagpt-lab/handoff/product-master-data/PRD-V1.0.md`，落入时把其中对 `README.md`、`系统设计.md` 及 D1–D7、Q1–Q10 的引用改为指向本 change 的 `design.md`（决策 1–11、Open Questions），验证：文档内没有指向 lab 目录的引用，`docs/README.md` 索引已加入
- [x] 9.2 同步更新 wiki 中 `Product-Master数据层架构决策.md`（仓库外，`~/Documents/llm-wiki/wiki/seo-geo/`）：写明价格 / 库存 / 状态不在 ERP 商品主表（决策 3），`ErpProductRepository` 在租户商品扩展出现前只能对接品牌 / 品类 / 系列 / 规格 / 型号关系，独立站暂不切换；并更正"8 张表"为实际 9 张、商品数 84 与实测 87 的差异，验证：wiki 文档与本 change 的 design.md 无冲突，并在 wiki `log.md` 留记录
- [ ] 9.3 完成定义检查，验证：`specs` 下 5 份规格的全部 scenario 均有对应通过的测试；`git diff` 确认没有修改任何 v1.0 / v1.1 已有表；写接口经过权限码与 `PlatformScopeGuard` 双重校验；任务 1.1 已完成；`openspec validate add-product-master-core --strict` 通过
