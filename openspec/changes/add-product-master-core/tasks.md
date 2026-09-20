## 0. 开发基础设施与框架扩展（拆分开发任务时发现的缺口）

- [x] 0.1 固化本地开发环境：后端固定用 JDK 17（本机是 Homebrew 的 `openjdk@17`，Maven 默认会拿到 Java 26，必须显式指定 `JAVA_HOME`），新增 `scripts/dev-env.sh` 导出 `JAVA_HOME` 并检查 MySQL、Redis 是否在运行，README 补一段"如何在本机跑后端测试"，验证：`source scripts/dev-env.sh && mvn -q -o test -Dtest=CustomerServiceImplTest` 通过
- [x] 0.2 建立集成测试基础设施（现有测试全是 Mockito 单测，没有 `@SpringBootTest`，没有 `src/test/resources`；本机没有 Docker，不能用 Testcontainers）：新建独立的 `zhul_erp_test` 库和 `application-test.yml`（测试库、Redis 用独立 db），提供 `sql/build/test/reset-test-db.sh` 依次执行 `schema_v1.sql`、`schema_v1.1.sql`、`schema_v1.2.sql`，`IntegrationTestBase` 基类，验证：一个冒烟集成测试通过，且能证明它连的是测试库而不是开发库
- [x] 0.3 扩展错误响应以承载字符串错误码和 `detail`（现有 `BizException` 只有数字 `code` 和 `message`，前端约定 `code: number`）：`BizException` 增加可选的 `errorCode`（字符串）和 `detail`；`GlobalExceptionHandler` 在 `Result.data` 中返回 `{errorCode, detail}`，数字 `code` 与 `message` 保持不变，向后兼容；前端 `requestErrorConfig.ts` 已经把 `data` 传给错误对象，只需在商品页读取 `errorCode`，验证：单元测试覆盖带与不带 `errorCode` 的两种异常；全部现有测试仍通过
- [x] 0.4 放宽上传体积上限：`spring.servlet.multipart.max-file-size` 与 `max-request-size` 由 5MB 提高到 100MB（视频要用）；询盘附件的 5MB 限制本来就在 `AttachmentStorageServiceImpl` 里自己校验，不受影响；`GlobalExceptionHandler.handleMaxUploadSizeExceeded` 的文案不能再写死"图片大小不能超过5MB"，改为通用文案，验证：`AttachmentStorageServiceImplTest` 仍通过

## 1. 前置确认

- [x] 1.1 确认平台账号登录后 JWT 的 `tenantId` 是否为 0（design.md Q2）：**用户于 2026-09-19 确认为 0**，已写回 design.md（决策 2 的前提、Open Questions 中 Q2 已划掉）。注：仓库内无账号种子数据，未在代码中独立核实，任务 3.3 的测试需固化这一约定
- [x] 1.2 与用户确认决策 3（商业数据与站点发布字段移出商品主表）：**用户于 2026-09-19 确认同意**，已写回 design.md（决策 3）。design.md 中 Q1 仅剩"租户商品扩展放哪个版本"，不影响本 change

## 2. 数据库

- [x] 2.1 新增 `sql/build/sql/schema_v1.2.sql`，按 design.md 决策 11 建 `product_brand`、`product_category`、`product_series`、`product`、`product_specification`、`product_relationship`、`product_document`、`product_application`、`product_faq`、`product_media`、`product_logistics`、`product_customs`、`product_reference_price` 十三张表，验证：在已执行 `schema_v1.sql`、`schema_v1.1.sql` 的本地 MySQL 8 上执行无报错、可重复执行；`SHOW CREATE TABLE` 与 design.md 一致；`git diff` 确认 v1.0 / v1.1 的 SQL 文件未被修改
- [x] 2.2 验证唯一键的大小写行为：向 `product_brand` 依次插入 `Siemens`、`siemens`，向 `product_series` 在同一品牌下依次插入 `S7-1200`、`s7-1200`，验证：第二次插入均报唯一键冲突（对应 `specs/product/brand/spec.md`、`specs/product/series/spec.md` 的"忽略大小写"）；若不冲突，给 `brand_name`、`series_name` 显式指定不区分大小写的排序规则并更新 design.md 的 DDL
- [x] 2.3 在 `resource` 表新增商品域菜单与按钮资源（`type=3`，权限码见 design.md 决策 2 的表），种子数据写法对照现有 `resource` 数据，验证：给某角色分配 `product:brand:add` 后 `PermissionChecker.has("product:brand:add")` 返回 true，未分配返回 false（集成测试）
- [x] 2.4 更新 `.claude/context/data-model.md`（v1.2 表清单、"tenant_id=0 平台共享"说明，并把 v1.2 从"后续版本规划"移入已落地）与 `docs/README.md`（v1.2.0 状态与 PRD 索引），验证：表清单与 `schema_v1.2.sql` 一致

## 3. 后端 — 基础（modules/product）

- [x] 3.1 建立 `com.zhul.erp.modules.product` 模块骨架（controller / service / repository / dto / entity / constants / support），十三张表的 Entity(DO) 与 Mapper，`LifecycleStatus`、`RelationshipType`、`RelationshipConfidence` 常量类与错误码（design.md 决策 10），验证：项目编译通过，常量取值与 design.md 一致（单元测试）
- [x] 3.2 实现 `MpnNormalizer`（NFKC → 小写 → 去掉所有非字母数字，design.md 决策 4），验证：单元测试覆盖 `6ES7 214-1BD23-0XB0`、`6ES7214-1BD23-0XB0`、首尾空格、全角 `６ＥＳ７２１４`、`SGMAH-04ADA-TF13`、`---`（空串）、`null`，覆盖率 100%
- [x] 3.3 实现 `PlatformScopeGuard`（要求 `TenantContext.getTenantId() == 0`），以及写接口统一使用的"权限码 + 平台账号"校验方式（design.md 决策 2），验证：单元测试覆盖 tenantId 为 0 通过、tenantId 为 1001 拒绝并返回 `PLATFORM_ADMIN_REQUIRED`、`TenantContext` 为空拒绝
- [x] 3.4 实现商品模块统一的租户处理：所有查询恒带 `tenant_id = 0`，写入时强制写 0 并忽略客户端传值（design.md 决策 1），验证：单元测试确认请求体带 `tenantId=1001` 时落库仍为 0；租户 1001 的登录用户读取到的数据与租户 1002 一致（对应 `specs/product/product/spec.md` "不同租户读到同一份商品"）

## 4. 后端 — 品牌 / 品类 / 系列

- [x] 4.1 实现品牌的创建、修改、启停、列表、`options`：名称必填且 ≤64、平台内唯一（忽略大小写与首尾空格）、`is_genuine` 默认 1，验证：单元测试覆盖 `specs/product/brand/spec.md` 的创建、重复（`siemens ` vs `Siemens`）、空名称、标记兼容品牌、停用后不出现在 options 的场景
- [x] 4.2 实现品牌删除保护：下面仍有未删除商品时拒绝并返回使用数量，否则软删除，验证：单元测试覆盖"有 5 个商品时拒绝且提示 5"与"无商品时软删除后正常查询不再返回"
- [x] 4.3 实现品类的创建、修改、启停、列表、`options`：编码格式 `^[a-z][a-z0-9_]*$` 且 ≤32、平台内唯一、有商品后编码不可修改（名称可改）、被商品使用时不可删除，验证：单元测试覆盖 `specs/product/category/spec.md` 的全部场景（含 `PLC Controllers` 格式非法、`drives` 有商品后改编码被拒、改名称成功）
- [x] 4.4 实现系列的创建、修改、启停、列表、`options`（支持 `brandId`）：归属已存在品牌、同品牌内名称唯一、不同品牌允许同名、有商品时不可删除，验证：单元测试覆盖 `specs/product/series/spec.md` 的全部场景
- [x] 4.5 实现品牌与品类 `options` 的 Redis 缓存（`zhul:erp:list:0:product_brand`、`zhul:erp:list:0:product_category`，TTL 300s，Cache-Aside），写库事务**提交后**再删缓存（`TransactionSynchronization.afterCommit`，design.md 决策 9），验证：集成测试确认修改品牌后缓存被清除；事务回滚时缓存不被清除
- [x] 4.6 给品牌 / 品类 / 系列的所有写接口加 `@PreAuthorize("@perm.has('product:...')")` 并接入 `PlatformScopeGuard`，验证：三类资源各有测试覆盖 `specs` 中"租户账号即使被分配权限仍被拒绝"场景，读接口对任意租户登录用户开放

## 5. 后端 — 商品（specs/product/product/spec.md）

- [x] 5.1 实现创建商品：品牌 / 品类 / 原始型号必填，`mpn_raw` 仅去首尾空格，`mpn_display` 默认等于 `mpn_raw`，系列须属于该品牌，生命周期默认 6，验证：单元测试覆盖"创建商品"与"系列不属于所选品牌被拒绝"场景
- [x] 5.2 实现归一化去重：命中同品牌未删除商品返回 `PRODUCT_DUPLICATE`（`detail.existingId`、`deleted=false`），命中已软删除商品返回 `deleted=true`，不同品牌允许相同型号，归一化为空返回 `PRODUCT_MPN_INVALID`，捕获 `DuplicateKeyException` 转 `PRODUCT_DUPLICATE`，验证：单元测试覆盖空格 / 大小写连字符 / 全角 / 不同品牌 / `---` 五个场景；集成测试用两个线程同时创建同一型号，断言只成功一个
- [x] 5.3 实现 `POST /products/{id}/restore`：恢复后 ID、规格、型号关系、技术资料、应用场景、FAQ、图片视频、物流、海关、参考价与删除前一致，验证：集成测试覆盖"新建命中已删除商品被拒绝"与"恢复已删除商品"
- [x] 5.4 实现生命周期规则：4 / 5 必须有 `lifecycle_source`（`PRODUCT_LIFECYCLE_SOURCE_REQUIRED`）；生命周期为 5 且存在类型 1/2/3 的关系时允许保存并在响应的 `warnings` 数组中给出提示，验证：单元测试覆盖 `specs/product/product/spec.md` 生命周期的三个场景
- [x] 5.5 实现商品启停：停用后不出现在选择器与匹配结果，已引用的历史单据不受影响，验证：单元测试覆盖"停用被引用的商品"（使用一个返回引用次数大于 0 的测试用 `ProductUsageChecker`）
- [x] 5.6 实现 `ProductUsageChecker` 扩展点与删除保护：`SELECT ... FOR UPDATE` 锁商品行 → 汇总所有 checker 的次数 → 大于 0 返回 `PRODUCT_IN_USE`（`detail.usageCount`），否则软删除；被引用后修改品牌或原始型号返回 `PRODUCT_MPN_IMMUTABLE`，其他字段可改（design.md 决策 6、9），验证：单元测试覆盖删除被引用 / 未引用、改型号被拒、改产品名称成功四个场景
- [x] 5.7 实现规格参数的读取与整体替换（`GET/PUT /products/{id}/specifications`），同一事务内先软删旧行再插入新行，同一集合内规格编码重复时整体拒绝且原规格不变，验证：集成测试覆盖"整体替换（3 条换成 2 条）"与"编码重复被拒且原规格不变"
- [x] 5.8 实现型号关系的增改删：关系类型与置信度枚举校验、不能关联自己、同商品下（关联型号归一化值 + 类型）不可重复、置信度为"已验证"时 `verified_by` 必填并由服务端填 `verified_at`、关联型号只命中唯一目录商品时自动填 `related_product_id` 而命中多个品牌时不填，验证：单元测试覆盖 `specs/product/product/spec.md` 型号关系的全部六个场景
- [x] 5.9 实现对称类型反向关系：类型 3/4/5/6 且 `createReverse=true` 时同一事务再写反向关系（两端都须是目录内商品），类型 1/2 忽略该参数，反向失败时整体回滚，验证：集成测试覆盖"对称类型创建反向关系""非对称类型不生成反向关系""反向创建失败整体回滚"
- [x] 5.10 实现商品分页列表与详情：列表支持 `keyword`、`brandId`、`categoryId`、`seriesId`、`lifecycleStatus`、`status`，`includeDeleted=true` 仅平台账号有效，详情含 `usageCount`，验证：单元测试覆盖筛选组合，租户账号传 `includeDeleted=true` 不返回已删除商品
- [x] 5.11 给商品、规格、型号关系、技术资料、应用场景、FAQ（含 approve）、图片视频（含上传、设主图）、物流、海关、参考价的所有写接口加 `@PreAuthorize` 与 `PlatformScopeGuard`，验证：测试覆盖 `specs/product/product/spec.md` 最后一条需求的四个场景（租户账号被拒、无权限码的平台账号被拒、`adminFlag=1` 的平台账号通过、不同租户读到同一份数据）

- [x] 5.12 实现技术资料的增删改与列表（`/products/{id}/documents`）：类型枚举校验、标题与文件地址必填、文件地址只允许 `http://`、`https://` 或单个 `/` 开头（拒绝 `javascript:`、`data:`、`//host`，返回 `DOCUMENT_URL_INVALID`）、同商品下未删除行内文件地址不可重复（`CONTENT_DUPLICATE`）、`verified=1` 时服务端填 `verified_at`，验证：单元测试覆盖 `specs/product/product/spec.md`「商品技术资料」的六个场景，地址校验的边界值（空串、大小写协议 `HTTPS://`、`/`、`//x`）另加用例
- [x] 5.13 实现应用场景的增删改与列表（`/products/{id}/applications`）：标题必填且 ≤64、说明 ≤500、同商品下标题忽略大小写与首尾空格不可重复，验证：单元测试覆盖「商品应用场景」的三个场景
- [x] 5.14 实现 FAQ 的增删改、列表与审核（`/products/{id}/faqs`、`PATCH .../approve`）：管理员新增恒为 `source=2`、修改内容不改 `source`、`approve` 只接受 `source=3`（否则 `FAQ_NOT_PENDING`）并写入 `reviewed_by`/`reviewed_at`、**所有返回 FAQ 的读取路径对租户账号在服务端过滤 `source=3`**（列表、单条），验证：单元测试覆盖「商品 FAQ 与发布审核」的六个场景；集成测试用 `tenantId=1001` 的登录用户确认读不到 `source=3` 行、平台账号能读到，审核后租户账号可读到

- [x] 5.15 实现商品媒体的存储接口与文件校验（`ProductMediaStorageService`，落 `zhul.upload.dir` 下的 `product` 子目录，可迁 OSS）：图片仅 `jpg/jpeg/png/webp` 且 ≤5MB，视频仅 `mp4/webm` 且 ≤100MB；同时校验扩展名和文件头；拒绝 SVG；存盘用随机文件名，不使用客户端文件名；流式写盘不整体读入内存；解析后的路径必须落在上传目录内（防路径穿越）；multipart 上限已在 0.4 放宽，验证：单元测试覆盖 `specs/product/product/spec.md`「商品图片与视频」中上传相关的场景（正常上传、超过大小、扩展名与文件头不一致、SVG），另加文件名含 `../`、空文件、大写扩展名 `PNG`；覆盖率 100%
- [x] 5.16 实现图片与视频的登记、上传、修改、删除、设主图（`/products/{id}/media`、`.../media/upload`、`.../main`）：上传接口加限流，外链与封面地址只允许 `http://`、`https://` 或单个 `/` 开头（`MEDIA_URL_INVALID`），设主图时 `SELECT ... FOR UPDATE` 锁商品行并在同一事务内取消原主图，视频设主图返回 `MEDIA_NOT_IMAGE`，验证：单元测试覆盖「商品图片与视频」的全部八个场景；集成测试用两个线程同时给同一商品设不同主图，断言最终只有一张主图
- [x] 5.17 实现物流信息的读取与整体保存（`GET/PUT /products/{id}/logistics`）：数值必须大于 0，毛重不得小于净重，未填写保持 NULL，验证：单元测试覆盖「商品物流信息」的四个场景，另加毛重等于净重（允许）与净重为 0.001（最小值）
- [x] 5.18 实现海关信息的读取与整体保存（`GET/PUT /products/{id}/customs`）：HS 编码去掉点和空格后须为 6–10 位数字并只存数字，原产国须是有效的 ISO 3166-1 alpha-2 大写码，出口退税率在 0–100 之间且保留 2 位小数，验证：单元测试覆盖「商品海关信息」的四个场景，HS 编码边界值（5 位拒绝、6 位通过、10 位通过、11 位拒绝、含字母拒绝、含空格与点通过）
- [x] 5.19 实现平台参考价（`GET/PUT/DELETE /products/{id}/reference-price`）：币种必填，金额必须大于 0，币种为 CNY 时汇率为 1，非 CNY 且汇率未维护时本位币为 NULL，本位币按 HALF_UP 保留 2 位、汇率保留 6 位；`DELETE` 为软删除，再次 `PUT` 复活原行；汇率由请求传入（与询盘报价一致，系统里没有汇率表，design.md Q12 已解决），未传汇率时本位币为空，验证：单元测试覆盖「商品平台参考价」的七个场景；计算边界：`0.005` 的舍入、汇率 6 位小数、超大金额乘汇率溢出 `decimal(18,2)` 时拒绝；覆盖率 100%
- [x] 5.20 实现档案完整度计算（design.md 决策 14、PRD 2.14）：10 个模块的完成条件，`GET /products` 与 `GET /products/{id}` 对平台账号返回 `completeness`（已完成数、总数、各模块是否完成），租户账号不返回；列表按当前页商品 ID 批量查各子表，不逐个商品查询，验证：单元测试覆盖 `specs/product/product/spec.md`「商品档案完整度」的六个场景；性能测试确认一页 20 个商品的完整度计算不多于 10 次查询；覆盖率 100%
- [x] 5.21 实现缺项筛选与统计：`GET /products?missing=media|logistics|customs|price`（`NOT EXISTS` 子查询，仅平台账号，租户传入被忽略）和 `GET /products/completeness-summary`，验证：单元测试覆盖「按缺项筛选商品」的三个场景，尤其是统计数量与筛选结果条数一致

## 6. 后端 — 商品查找（specs/product/product-lookup/spec.md）

- [x] 6.1 实现 `GET /products/search`：关键词归一化后走 `idx_mpn_normalized` 前缀匹配，同时按产品名称 `LIKE` 匹配（转义 `%`、`_`）；**归一化后为空时跳过型号前缀分支**；只返回启用且未删除的商品与精简字段；`limit` 缺省 20、最大 50、非正数按缺省；空关键词返回空列表，验证：单元测试覆盖该 spec 选择器搜索的五个场景（空格与大小写不影响命中、只返回启用未删除、结果上限、空关键词、按产品名称匹配），另加一条"关键词为 `---` 时不返回全部商品"
- [x] 6.2 实现 `GET /products/match`：品牌按忽略大小写与首尾空格比较，返回 `exact`（同品牌同归一化型号）与 `candidates`（其他品牌同归一化型号 + 前缀匹配，最多 10 条），只返回启用且未删除的商品，验证：单元测试覆盖该 spec 型号匹配的五个场景
- [x] 6.3 汇总契约测试与覆盖率：所有接口响应符合 `{"code":0,"data":{},"message":"ok"}`，错误响应为数字 `code` + `message`，`data` 中含 `errorCode`/`detail`（design.md 决策 10，任务 0.3），验证：整体语句覆盖率 ≥70%，`MpnNormalizer`、生命周期规则、型号关系规则、FAQ 发布门禁与审核、媒体文件校验、参考价换算与舍入覆盖率 100%，`openspec validate add-product-master-core --strict` 通过
- [x] 6.4 给 `GET /products/search` 增加可选参数 `brandId`（限定品牌，向导第 1 步提示相近型号用）：归一化后的前缀匹配，只返回该品牌下启用且未删除的商品，验证：单元测试覆盖 `specs/product/product-lookup/spec.md`「相近型号提示」的两个场景

## 7. 前端（zhul-erp-frontend/src/pages/product/）

- [x] 7.1 用 OpenPencil 出商品域原型，存入 `docs/03-产品原型/02-商品域/00-商品主数据/`，结构与 Design Token 遵循 `.claude/context/ui-design-patterns.md`（含 2026-09-19 新增的 V3 方案 D 视觉语言），验证：`.op` 文件与效果图已提交并经用户确认；**7.2 起的页面任务以此为准**。**进度（2026-09-19）**：V3 方案 D（信任蓝 + 渐变，深色默认）版已出稿 `商品主数据.op`，29 个画板：新建 5 步向导 7 个（含型号已存在、曾被删除）、商品列表 2 个、品牌 / 品类 / 系列列表 3 个、商品档案 7 个（总览、图片视频、规格编辑、FAQ 审核、租户只读、物流海关填写、参考价填写）、弹窗与选择器 10 个，每个画板旁有交互标注；待用户确认
- [x] 7.2 实现品牌、品类、系列三个列表页与新增/编辑弹窗（ProTable / ProForm）与各自的 `service.ts`：被使用的记录删除按钮置灰并提示使用数量，写操作按钮按权限码隐藏，验证：类型检查与 lint 通过，手动走查新增、重名提示、停用、删除被使用记录被拒
- [x] 7.3 实现商品列表页（原型 L、Lb）：搜索优先（`/` 聚焦、输入即搜防抖 300ms、显示归一化关键词）、品类药丸、缺项快捷筛选与统计提示条（仅平台账号）、「档案完整度」列（10 段小进度条）、整行可点进入档案、状态开关二次确认、记住上次筛选、无结果空状态带「新建」、列头排序（`aria-sort`）、复选框与批量操作条（批量启用 / 停用 / 导出）、骨架屏与失败重试，验证：类型检查与 lint 通过，手动走查搜索 `6ES7 214` 与 `6es7214` 结果一致，缺项筛选数量与提示条一致
- [x] 7.4 实现商品档案页（原型 D、D2–D7）：顶部主卡与被引用提示条、档案完整度卡片与待补清单（点击滚动到对应卡片，支持 `?focus=`）、主栏 6 张卡片（基本信息 / 图片与视频 / 规格参数 / 物流与海关 / 平台参考价 / 型号关系）加技术资料、应用场景、FAQ 三张小卡，侧栏状态与信息；每张卡片原地编辑并独立保存，有未保存修改时浮出深色保存条并拦截离开；空卡片写明用途和主操作；平台参考价标注"平台参考价，不是报价"，汇率未维护显示"未计算"；FAQ 待审核行有"确认"气泡二次确认；租户视角隐藏完整度、状态栏和所有写操作，验证：类型检查与 lint 通过，手动走查 PRD 5.2、5.3、5.5、5.6 流程
- [x] 7.5 实现 `ProductSelect` 选择器组件（远程搜索、输入防抖、只显示启用商品、展示"展示型号 · 品牌 · 品类"），验证：类型检查与 lint 通过，在商品列表页之外的任一页面引用该组件可正常搜索选择
- [x] 7.6 接入路由与菜单（含 `/product/products/new`），验证：菜单与 2.3 的 `resource` 数据一致，租户账号登录后只看到只读入口，写按钮不可见
- [x] 7.7 实现新建商品 5 步向导（原型 W1–W5，路由 `/product/products/new`）：步骤条、右侧商品卡实时预览、第 1 步去重三态（可以新建 / 已存在 / 曾被删除）与相近型号、第 2 步点「创建并继续」时调用创建接口、第 3 步上传图片（前端预检、进度、可取消、可跳过）、第 4 步补充资料后点「完成」、第 5 步完成页；第 1、2 步草稿存浏览器本地（键带账号标识、7 天过期），刷新后提示继续，创建后清除，验证：类型检查与 lint 通过；手动走查 PRD 验收项 20–22，包括刷新恢复草稿、并发重复时停留在第 1 步
- [x] 7.8 实现档案完整度组件（10 段进度条、待补清单、预计用时、列表小进度条，8 段及以上绿色），供列表页、档案页和向导完成页复用，验证：组件对 0 / 1 / 7 / 10 段的展示正确；租户账号不渲染该组件
- [x] 7.9 接入主题令牌：以 Ant Design 的深色算法为默认、浅色算法并存，令牌取值见 `ui-design-patterns.md` 的 V3 一节（`colorPrimary` `#2563EB`、`colorLink`、`colorBgBase`、`colorBgContainer`、`colorBorder`、圆角），提供主题切换并记住选择；主按钮用 `#2563EB → #4F46E5` 渐变，验证：深色与浅色下正文、链接、主按钮文字的对比度都不低于 4.5:1，焦点框可见；本项只覆盖商品页，全站主题升级另立 change
- [x] 7.10 按 `ui-design-patterns.md`「可访问性与规范补充」做可访问性验收：深色和浅色主题的对比度（正文 ≥4.5:1、控件边界 ≥3:1）、可见焦点与键盘全流程（含向导和弹窗）、交互目标 ≥24×24、`prefers-reduced-motion`、`aria-sort`、拖拽替代按钮、等宽数字，验证：用 axe 或 Lighthouse 扫描列表、向导、档案页三类页面无严重问题，并手动走查键盘流程

## 8. 数据迁移（一次性）

- [x] 8.1 检视 `js/data.js` 中 `compatibility` 的子字段结构（design.md 迁移表中仍标为"未检视"），把结论补进 design.md 迁移映射，验证：迁移映射表中 `compatibility` 一行不再含"未检视"。已知（2026-09-19 实测）：`datasheet`、`applications`、`faq` 的结构已检视并写入迁移表；`compatibility` 为 12 个商品共 22 条 `{from, type?, note}`，其中 7 个商品带 `type`（如 `direct`/`compatible`/`functional`），5 个商品没有 `type`，`type` 到关系类型的映射待定
- [x] 8.2 编写迁移脚本（Node，放入 `scripts/product-migration/`）的**干跑模式**：读取 `fouwell-website/js/data.js`，只输出统计与异常，验证：输出商品 87 / 品牌 40 / 品类 6 / 系列 80，并报告同品牌下归一化型号碰撞、品牌名近似重复、空型号、缺品类，结果与 2026-09-19 的预干跑一致（均为 0 项异常）；另输出技术资料 1 条、应用场景 117 条（84 个商品）、FAQ 222 条（84 个商品）、参考价迁入 56 条 / 排除 14 条（`procurement_quote_min`）、实拍图 52 张（不迁移）、规格里的重量 1 / 尺寸 1 / 原产国 26 条自由文本（不自动解析），以及**疑似卖家承诺的 FAQ 完整清单与命中关键词**（design.md 迁移表）和技术资料文件是否存在（预期不存在）；脚本**不写库**
- [x] 8.3 实现导入模式：生成幂等 SQL（商品主体和参考价（按唯一键 `uk_tenant_product`）用 `INSERT ... ON DUPLICATE KEY UPDATE`，内容类三张表用 `NOT EXISTS`），所有行 `create_by='migration'`；被排除的卖家类 FAQ 不生成 SQL，误伤条目通过人工放行清单加回，同时生成回滚 SQL，验证：空库导入后各表计数与 8.2 一致（FAQ = 222 − 140 = 82，全部 `source=3`；参考价 56 条，全部 USD、本位币为空；图片视频、物流、海关为 0 行）；重复执行一次计数不变；执行回滚后所有迁移行 `deleted_at` 非空
- [x] 8.4 抽查迁移结果，验证：随机抽 5 个商品，其规格参数逐项与独立站对应页面一致；生命周期映射符合 design.md（`instock`→1、`legacy`→3、`discont`→4、带 `no_known_replacement` 的 1 条→5）；参考价随机抽 5 条与独立站 `sell_price` 一致（USD、本位币为空），`procurement_quote_min` 的 14 条和 52 张实拍图未被迁入；导入的 FAQ 中没有任何一条含 `Fouwell`，随机抽 5 条 FAQ 与独立站页面一致，应用场景的 `icon` 保持 emoji 原样

## 9. 文档与收尾

- [x] 9.1 将 PRD 落入 `docs/02-产品PRD/02-商品域/00-商品主数据/商品主数据-PRD-V1.0.md`（仿照 `03-业务域/00-询盘中心/00-询盘单/` 的层级；`02-商品域` 的编号按 v1.2 在版本规划中的顺序推断，用户于 2026-09-19 认可）。来源为 `~/Documents/zhul-metagpt-lab/handoff/product-master-data/PRD-V1.0.md`，落入时把其中对 `README.md`、`系统设计.md` 及 D1–D7、Q1–Q10 的引用改为指向本 change 的 `design.md`（决策 1–11、Open Questions），验证：文档内没有指向 lab 目录的引用，`docs/README.md` 索引已加入
- [x] 9.2 同步更新 wiki 中 `Product-Master数据层架构决策.md`（仓库外，`~/Documents/llm-wiki/wiki/seo-geo/`）：写明价格 / 库存 / 状态不在 ERP 商品主表（决策 3），`ErpProductRepository` 在租户商品扩展出现前只能对接品牌 / 品类 / 系列 / 规格 / 型号关系，独立站暂不切换；并更正"8 张表"为实际 9 张、商品数 84 与实测 87 的差异，验证：wiki 文档与本 change 的 design.md 无冲突，并在 wiki `log.md` 留记录
- [ ] 9.3 **（进度 2026-09-20：`git diff` 确认 v1.0 / v1.1 的 SQL 未改；写接口的权限码 + 平台账号双重校验已由集成测试和 HTTP 契约测试覆盖；`openspec validate --strict` 通过；规格场景已按场景逐条写测试，但没有做自动的场景到测试的映射核对；依赖 7.10 完成后再勾选）** 完成定义检查，验证：`specs` 下 5 份规格的全部 scenario 均有对应通过的测试；`git diff` 确认没有修改任何 v1.0 / v1.1 已有表；写接口经过权限码与 `PlatformScopeGuard` 双重校验；任务 1.1 已完成；`openspec validate add-product-master-core --strict` 通过
