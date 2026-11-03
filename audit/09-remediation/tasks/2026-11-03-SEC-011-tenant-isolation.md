# SEC-011 分类租户拦截器与系统表隔离（批次 B 第一部分）

## 范围与负责人

| 字段 | 内容 |
| --- | --- |
| 负责人 / 协作者 | Claude（实施与验证）；用户 Ezio 决定方案、合并、发布与验收 |
| 关联 ID / 原专题 | [SEC-011](../../07-security/02-身份会话与对象权限.md)（新发现，根因）、[SEC-002](../../07-security/02-身份会话与对象权限.md)、[SEC-003](../../07-security/02-身份会话与对象权限.md)、[SEC-009](../../01-business/05-全量业务代码审查.md)、[SEC-010](../../07-security/03-全量平台与商品代码审查.md)、[BUS-002](../../01-business/02-销售与财务.md) |
| 目标与影响范围 | 方案 C 的基础设施，加上批次 B 涉及的系统表、供应商档案、客户退款启用自动租户过滤，并修上述各项。其余模块的表按后续分支逐个启用；SEC-005/006/007 在下一个分支处理 |
| 起点 commit / 工作分支 | `develop` `f705aaeec3bae53a4eb38e8992ac99fb4ebeeee7`；`fix/tenant-isolation-core`。用户 2026-11-03 要求：租户修复的各分支完成后不合并，从上一个分支继续拉新分支，最终合并到 `develop` 由用户确认 |
| OpenSpec | 不适用：安全缺陷修复，不改变规格描述的业务行为；编码规范「多租户规范」在全部模块启用后的分支里同步修改 |
| PR / 目标分支 | 未创建（按用户要求，链式分支全部完成后统一确认） |
| 合并证据 | 未合并 |
| 目标版本 / 环境 | 待定 / 生产 |

## 方案与实施

用户 2026-11-03 在方案 A（统一租户拦截器）、方案 B（手写条件 + 测试检查）、方案 C（分类租户拦截器、按模块逐步启用）中选定方案 C。

- **`framework/tenant/TenantTables`**：已纳入拦截器的表及隔离方式。本分支启用：`user_basic`、`account`、`department`、`position`、`supplier`、`supplier_attachment`、`supplier_bank_account`、`supplier_product_scope`、`fin_customer_refund`（私有）；`role`、`sys_config`（共享只读）；`dict_type`、`dict_item`（共享可写，PRD 允许租户维护全局字典项）。
- **`framework/tenant/TenantInterceptor`**（继承 MyBatis-Plus 3.5.6 `TenantLineInnerInterceptor`）：私有表 `tenant_id = 当前租户`；共享表查询 `tenant_id IN (0, 当前租户)`，共享只读表的 UPDATE/DELETE 仍是 `= 当前租户`；没有租户上下文直接报错。只处理出现已启用表名的 SQL，其余原样放行（见下方「改写问题」）。
- **`framework/tenant/TenantScope`**：平台范围，范围内不追加租户条件。用在本来就跨租户的入口：登录、刷新 Token、找回密码三个接口，权限计算（按全局唯一的用户名解析账号），登录页外观，平台租户管理与初始化数据，历史迁移后台任务，11 个启动回填、定时任务与恢复转码入口，新建用户时的用户名全局唯一检查，删除角色时的在用人数统计（平台角色被各公司引用）。
- **开关**：`zhul.tenant.enforce`（环境变量 `TENANT_ENFORCE`，默认 `true`）。compose 与 `application.yml` 给了默认值，`.env.example` 只有注释说明，生产 `.env` 不用改；线上故障时加 `TENANT_ENFORCE=false` 重新部署即可临时关闭。
- **SEC-002**：用户启停、编辑、删除、管理员重置密码先加载目标用户，加载不到报「用户不存在」，不把影响 0 行当成功；新建、编辑时引用的部门、岗位须是本租户的，角色可以是本租户的或平台内置的；用户名唯一性在平台范围检查。
- **SEC-003**：角色修改、删除、分配权限要求角色属于当前租户，平台内置角色报「平台内置角色不能修改」（可见、可引用）；`role_org`、`role_resource` 没有租户字段，归属校验放在删除旧关联之前。部门的编辑、启停、删除及上级部门均按 ID 加载，其他租户的部门报「不存在」。
- **SEC-009**：供应商按 ID 加载由拦截器限定本租户；详情读不到其他租户时返回空，编辑、启停、删除报「供应商不存在」；子表一并启用。
- **SEC-010**：配置、字典按 ID 加载由拦截器限定；租户删除平台配置报「平台配置不能删除」；租户修改平台配置模板时，沿用原来的「复制到本租户」逻辑；租户维护平台字典项按 PRD 保留。
- **BUS-002**：退款查询加显式租户条件；只有销售订单菜单、没有退款管理菜单的人查看订单退款或退款详情时，订单须在其数据范围内，否则报「销售订单不存在」。
- **行为变化**：已删除的用户不能再改状态（以前静默不处理）；租户不能再删除平台配置；销售侧看订单退款要受数据范围限制。
- **测试夹具调整**：部分测试在 MockMvc 请求结束后直接调用服务，请求结束时租户上下文已被清掉；按真实请求补上了租户上下文。`IntegrationTestBase` 新增 `testUserTenant`，工作台测试让账号、用户和令牌的租户一致，并给测试租户配了包含全部菜单的套餐。

## 实施与验证结果

| 命令 / 操作与证据 | 环境、commit / 版本 | 预期 | 实际 | 结论 |
| --- | --- | --- | --- | --- |
| 静态解析：Mapper 全部 76 条注解 SQL 交给项目依赖的 JSqlParser 4.9 | 本机；`develop` `f705aaee` | 能解析 | 47 条静态 SQL 全部能解析；29 条 `<script>` 要运行时拼接 | 通过 |
| 运行时演练：临时探针在全量测试中对每条 SQL 调用真实 `TenantLineInnerInterceptor` 改写（不执行） | 本机；`develop` `f705aaee`；探针已还原 | 能改写 | 2131 条不同 SQL 中 2128 条成功；3 条失败均因 `ai_task.output` 是 JSqlParser 保留字 | 通过；遗留见下 |
| `mvn test -Dtest=TenantIsolationContractTest`（[测试源码](../../../zhul-erp-backend/src/test/java/com/zhul/erp/framework/tenant/TenantIsolationContractTest.java)） | 本机；临时检出 `develop` 修复前代码（去掉依赖新类的 1 个用例） | 失败 | 5 个全部失败：跨租户的用户、角色、部门、配置、字典都能修改，供应商详情能读到 | 缺陷见证成立 |
| 同上 | 本机；`fix/tenant-isolation-core` 工作区 | 通过 | 6 个通过 | 通过 |
| `mvn test -Dtest=TenantInterceptorTest`（[测试源码](../../../zhul-erp-backend/src/test/java/com/zhul/erp/framework/tenant/TenantInterceptorTest.java)） | 本机，不依赖 Spring | 各类表生成对应条件；平台范围与关闭开关时不改写；缺上下文报错 | 6 个通过 | 通过 |
| `mvn test`（后端全量） | 本机 Docker MySQL/Redis；工作区 | 无失败 | 1214 个，0 失败，6 个环境跳过 | 通过 |
| 浏览器 / 生产 | — | — | 未执行 | 全部模块启用并合并后验收 |

失败与过程说明：第一次全量运行时，未启用的表的 SQL 也被拦截器解析并重新拼接，JSqlParser 4.9 把 `ORDER BY … LIMIT 1 FOR UPDATE` 拼成 `FOR UPDATE ORDER BY …`，采购等模块 70 次请求报 SQL 语法错误。前一次探针只检查了「能否改写」，没有检查「改写结果能否执行」。已改为只处理出现已启用表名的 SQL；本分支已启用的表没有「排序 + FOR UPDATE」写法（已核对）。

## 遗留与后续模块启用前必须处理

- **`FOR UPDATE` 拼接问题**：启用采购（`purchase_order`、`purchase_requirement`）、收款（`payment_receipt`）等模块前，需改写对应 SQL 或绕开 JSqlParser 的重新拼接，并用全量测试验证改写后的 SQL 能执行。
- **`ai_task.output` 保留字**：启用 AI 任务表前，在实体上给字段加反引号。
- **JdbcTemplate**：4 个迁移工具类直接写 SQL，不经过拦截器，均为平台按指定租户操作；`HistoryMigrationService` 按 `inquiry_item_id IN (…)` 处理 `product_candidate_source` 时未带租户条件（ID 来自带租户条件的迁移记录），在启用迁移相关模块时补上。
- **分类测试**：全部模块启用后，加测试保证每张带 `tenant_id` 的表都已分类；同时修改编码规范「多租户规范」。

### 带 tenant_id 的表（144 张）候选分类

`*` 表示本分支已启用；`[共享?]` 表示代码里有读取平台（tenant 0）数据的写法，候选为共享表，其余候选为私有表。每个模块启用时再逐张核对。

- **account**：account*
- **after**：after_sales, after_sales_item, after_sales_log
- **ai**：ai_task
- **biz**：biz_attachment, biz_attachment_model
- **courier**：courier_waybill
- **customer**：customer, customer_inquiry, customer_inquiry_append, customer_inquiry_attachment, customer_party
- **department**：department*
- **dict**：dict_item* [共享?], dict_type* [共享?]
- **document**：document_sequence, document_template [共享?], document_template_version [共享?]
- **exchange**：exchange_rate [共享?], exchange_rate_log
- **fin**：fin_customer_credit_consumption, fin_customer_credit_entry, fin_customer_refund*, fin_expense, fin_expense_allocation, fin_fx_settlement, fin_fx_settlement_allocation, fin_other_income, fin_payable, fin_payable_installment, fin_payment, fin_payment_allocation, fin_refund_request, fin_reimbursement, fin_reimbursement_line
- **forwarder**：forwarder_statement, forwarder_statement_line
- **freight**：freight_allocation
- **inquiry**：inquiry_item, inquiry_order, inquiry_order_item, inquiry_order_item_quote, inquiry_order_supplier
- **listing**：listing_task
- **logistics**：logistics_shipment
- **media**：media_asset, media_item
- **migration**：migration_batch, migration_id_map
- **opportunity**：opportunity, opportunity_attachment, opportunity_stage [共享?], opportunity_stage_log
- **outbound**：outbound_box, outbound_box_item, outbound_order, outbound_order_item
- **payment**：payment_receipt
- **position**：position*
- **pricing**：pricing_amount_tier, pricing_condition_margin
- **product**：product [共享?], product_application [共享?], product_brand [共享?], product_brand_alias [共享?], product_candidate, product_candidate_source, product_category [共享?], product_content_import, product_content_task, product_customs [共享?], product_document [共享?], product_fact_note [共享?], product_fact_task [共享?], product_faq [共享?], product_keyword, product_keyword_use, product_listing_price [共享?], product_locale, product_logistics [共享?], product_marketing_skip, product_media [共享?], product_order_code [共享?], product_platform_content, product_platform_keyword, product_reference_price [共享?], product_relationship [共享?], product_relationship_note, product_seo, product_seo_faq, product_series [共享?], product_specification [共享?]
- **proforma**：proforma_invoice, proforma_invoice_fee, proforma_invoice_item, proforma_invoice_send_log, proforma_invoice_version
- **purchase**：purchase_order, purchase_order_attachment, purchase_order_fee, purchase_order_item, purchase_order_log, purchase_receipt, purchase_receipt_item, purchase_requirement
- **quotation**：quotation, quotation_fee, quotation_item, quotation_send_log, quotation_version
- **receiving**：receiving_discrepancy
- **resource**：resource
- **role**：role* [共享?]
- **sales**：sales_order, sales_order_cost_snapshot, sales_order_fee, sales_order_item, sales_order_partial_cancel, sales_return, sales_return_disposition, sales_return_item
- **shipment**：shipment_doc_group
- **shoot**：shoot_task
- **sourcing**：sourcing_assign_rule, sourcing_import, sourcing_item_handoff, sourcing_quote, sourcing_task, sourcing_task_assignee
- **stock**：stock_hold
- **supplier**：supplier*, supplier_attachment*, supplier_bank_account*, supplier_product_scope*, supplier_shipment, supplier_shipment_item
- **sys**：sys_ai_config, sys_ai_provider_config, sys_config* [共享?], sys_log
- **tenant**：tenant_bank_account
- **transit**：transit_time
- **user**：user_basic*

## 第三个分支 `fix/tenant-modules-trade`：交易链路启用（2026-11-03）

从 `fix/tenant-object-guards` `4e0f2aac` 拉出。

- **JSqlParser 加锁子句（已处理）**：`TenantInterceptor.beforeQuery` 改写前截下语句末尾的 `FOR UPDATE` / `FOR SHARE` / `LOCK IN SHARE MODE`（含 `NOWAIT`、`SKIP LOCKED`），改写后原样接回，不再交给 JSqlParser 重新拼接。单元测试覆盖 `ORDER BY … LIMIT 1 FOR UPDATE`、`ORDER BY id FOR UPDATE`、`LOCK IN SHARE MODE`。
- **`ai_task.output` 保留字（已处理）**：`AiTaskDO.output` 加 `@TableField("`output`")`；单元测试确认不加反引号会解析失败、加了能正常改写。
- **拼接一致性演练**：临时探针在全量测试中，对每条实际执行的 SQL 截下加锁子句、解析再拼回，与原文比较（去掉空白、反引号、大小写后比较）。2084 条不同 SQL 全部一致，0 条解析失败；探针已还原。
- **启用的表**：财务 15 张、客户与商机 6 张（`opportunity_stage` 共享只读）、询盘与询价 15 张、报价 / PI / 销售订单 / 收款 / 售后 19 张（`pricing_amount_tier`、`pricing_condition_margin` 共享只读：平台默认值 + 租户自己的规则），共 55 张；累计启用 68 张。
- **AI 回调与异步线程**：回调接口不带用户令牌，`AiTaskServiceImpl.handleCallback` 改为在平台范围内按任务 ID 找到任务，再用 `TenantContext.runAs(任务所属租户)` 处理结果；提交任务后的异步结果处理（编排服务拒收时通知发起方失败）同样按任务租户执行。不修的话，回调接口返回 500，询盘永远收不到解析结果（已用新测试在撤掉修复时见证）。
- **测试**：新增 `AiCallbackTenantContractTest`（走真实回调接口）；`TenantIsolationContractTest` 新增「启用清单里的每张表都存在并有 `tenant_id`」，防止表名写错导致悄悄不生效；若干测试在直接调用服务前补上租户上下文。
- **验证**：后端全量 1222 个，0 失败，6 个环境跳过；测试日志中「缺少租户上下文」0 次、SQL 语法错误 0 次。

## 第四个分支 `fix/tenant-modules-supply`：供应链启用（2026-11-03）

从 `fix/tenant-modules-trade` `a052e0b0` 拉出。启用采购与收货 11 张、仓储出库与物流 12 张、附件与素材 4 张，共 27 张，全部私有；累计启用 95 张。采购模块的 `FOR UPDATE` 语句由上一分支的加锁子句处理覆盖（拼接一致性演练包含全部表的 SQL）。

- **验证**：后端全量 1222 个，0 失败；测试日志中「缺少租户上下文」0 次、SQL 语法错误 0 次。`TenantInterceptorTest` 里「未启用的表」示例由 `purchase_order` 换成全局表 `tenant_package`。

## 第五个分支 `fix/tenant-modules-platform`：商品内容、AI 与其余系统表（2026-11-03）

从 `fix/tenant-modules-supply` `da3bf8f7` 拉出。144 张带 `tenant_id` 的表全部归类完毕：拦截器管辖 118 张，明确排除 26 张。

- **启用**：私有 14 张，包括 `product_content_task`、`product_keyword`、`product_keyword_use`、`product_marketing_skip`、`product_platform_content`、`product_platform_keyword`、`product_seo`、`product_seo_faq`、`listing_task`、`ai_task`、`sys_log`、`document_sequence`、`exchange_rate_log`、`tenant_bank_account`。共享只读 6 张：`sys_ai_config`、`sys_ai_provider_config`、`document_template`、`document_template_version`、`exchange_rate`、`transit_time`。
- **排除清单 `TenantTables.EXCLUDED`**，每张表写明理由：
  - 全局商品库 20 张（`product` 及其资料、品牌、类目、系列等），数据一律存 tenant 0。租户能否修改看 `owner_tenant_id`，由 `ProductWriteGuard` / `PlatformScopeGuard` 校验。起初按共享只读启用，租户改自己的商品时更新语句被追加 `tenant_id = 本租户`，匹配 0 行：删除不生效，恢复时报「商品不存在」（`TenantProductEditingContractTest`、`ProductContentPlanContractTest` 失败）。
  - `product_content_import`：资料包上传记录各公司可见；营销包由 `ContentImportRecordService` 显式带租户条件。
  - `resource`：全局菜单。
  - 候选池与历史迁移 4 张。
- **新增守卫**：`TenantIsolationContractTest.everyTenantTableIsClassified`，库里每张带 `tenant_id` 的表都必须在启用或排除清单里，新建表漏分类时测试失败。
- **代码修复**：资料核对是共享操作（`ListingTaskSync` 收到无租户的营销变化事件）。改为在平台范围查出相关公司，再用 `TenantContext.runAs` 以每家公司的租户同步发品任务。缺陷见证：修复前 `ListingTaskContractTest.generate_byPlatform_cancel_restore_acrossCompanies` 失败，B 公司的阿里国际站任务未生成（`{alibaba=1}` 对 `{}`）。
- **测试调整**：MockMvc 请求结束会清掉租户上下文，所以询盘报价与子类目重分测试在请求后直接调服务前补设租户。
- **验证**：后端全量 1224 个，0 失败，6 个环境跳过；测试日志中「缺少租户上下文」0 次，SQL 语法错误与 JSqlParser 解析异常 0 次。

## 第六个分支 `fix/tenant-rules-doc`：编码规范同步（2026-11-03）

从 `fix/tenant-modules-platform` `10d5ed74` 拉出。

- `.claude/rules/coding.md`「多租户规范」改为与实现一致，内容包括：
  - 分类拦截器与三种隔离方式
  - 没有租户上下文时报错
  - 新表必须归类
  - `TenantScope.platform` 与 `TenantContext.runAs` 的用法
  - JdbcTemplate 须手写租户条件
  - JSqlParser 保留字与加锁子句的注意事项
- `HistoryMigrationService` 撤销迁移时，候选来源（`product_candidate_source`）的查询与软删除补上 `tenant_id = 批次租户`。原来只靠来自带租户条件迁移记录的 ID，现在改为纵深防御。
- **验证**：`HistoryMigrationContractTest`、`HistoryMigrationReadOnlyContractTest`、`TenantIsolationContractTest` 通过；前一分支全量 1224 个 0 失败。

## 合入 develop 新提交（2026-11-03）

`develop` 新增 22 个提交，含 v1.6.0 发版，在 `fix/tenant-rules-doc` 上合入。新代码按租户规则做了以下适配：

- 新建的 5 张表 `customer_contact`、`customer_field_source`、`customer_dup_dismiss`、`customer_background_report`、`country_profile` 都由代码按本租户读写，归为私有。合入后 `everyTenantTableIsClassified` 先报出这 5 张未归类，归类后通过。累计启用 123 张。
- **凌晨定时任务 `CustomerNightlyJob.run`**：没有租户上下文，第一句查询就被拦截器拒绝，整个任务失败。现改为在平台范围查出租户后逐个处理。测试由直接调 `recompute` 改为走 `run()`。缺陷见证：撤掉修复时 `CustomerProfileContractTest` 报「缺少租户上下文」，恢复后通过。
- `CustomerMergeService.countBy` 是 JdbcTemplate，补上 `tenant_id` 条件。客户 ID 本来就来自本租户，这是纵深防御。
- 联系人中文名翻译的测试直接调 `handle`，改为在任务所属租户下调用，与生产中回调的 `runAs` 一致。
- 规则可见性：
  - `AGENTS.md` 的 ALWAYS 清单加一行租户规则（用户 2026-11-03 同意）。
  - 编码规范「建表规范」补一句「新表须在 `TenantTables` 归类」。
- **验证**：
  - 后端全量 1296 个，0 失败，7 个环境跳过。
  - 统计报告（`*.txt`、`*.xml`、`*-output.txt`）中「缺少租户上下文」0 次，SQL 语法错误与 JSqlParser 异常 0 次。此前几轮只统计了 `*-output.txt`，这类异常其实写在 `*.txt` 报告里，所以口径不全；本轮已补全。本轮是合并后的最终代码，结论覆盖此前各分支。
- 复核 develop 新提交的其他发现见 [2026-11-03-BUS-025](2026-11-03-BUS-025-develop-merge-review.md)。

## 合并、部署与逐 ID 验收

| ID | 修复 / 验收范围 | 部署与目标环境验收证据 | 验收人 / 日期 | 结论与剩余项 |
| --- | --- | --- | --- | --- |
| SEC-011 | 拦截器基础设施；144 张带 tenant_id 的表全部归类（启用 118、排除 26），新表漏分类由测试拦截；编码规范已同步 | 未部署 | — | 待合并：六个链式分支待用户确认合并到 `develop` |
| SEC-002 | A 对 B 的启停、编辑、删除、重置均拒绝且数据不变；引用他租户部门被拒；用户名全局唯一 | 未部署 | — | 待合并 |
| SEC-003 | 他租户角色、部门拒绝；平台角色只读；旧关联在拒绝时保留 | 未部署 | — | 待合并 |
| SEC-009 | 他租户供应商详情读不到，编辑、启停、删除拒绝 | 未部署 | — | 待合并 |
| SEC-010 | 他租户配置、字典拒绝；平台配置不能删；平台字典项仍可维护 | 未部署 | — | 待合并 |
| BUS-002 | 他租户退款不可见；销售侧受数据范围限制 | 未部署 | — | 待合并 |

## 阻塞与下一步

- 链式分支全部完成，等待用户确认合并到 `develop`。合并 `fix/tenant-rules-doc` 即包含前面五个分支的全部提交。

## 事件记录

| 日期 / 作者 | 事件与证据 | 下一步 / 交接 |
| --- | --- | --- |
| 2026-11-03 / Claude | 用户选定方案 C；完成 SQL 静态解析与运行时改写演练；新编 SEC-011；在 `fix/tenant-isolation-core` 实施并验证（见上表） | 拉下一个分支继续 |
| 2026-11-03 / Claude | `fix/tenant-isolation-core` 提交 `2c440cb2` 并推送（未建 PR、未合并）；从它拉 `fix/tenant-object-guards` 处理 SEC-005/006/007，见 [2026-11-03-SEC-005](2026-11-03-SEC-005-object-guards.md) | 之后按模块逐个启用其余表 |
| 2026-11-03 / Claude | `fix/tenant-object-guards` 提交 `4e0f2aac`；从它拉 `fix/tenant-modules-trade`，处理 JSqlParser 加锁子句与 `output` 保留字，启用交易链路 55 张表并修 AI 回调租户 | 继续启用采购、仓储物流、商品库等其余表 |
| 2026-11-03 / Claude | `fix/tenant-modules-trade` 提交 `a052e0b0`；从它拉 `fix/tenant-modules-supply`，启用供应链 27 张表 | 启用商品库与其余平台表 |
| 2026-11-03 / Claude | `fix/tenant-modules-supply` 提交 `da3bf8f7`；从它拉 `fix/tenant-modules-platform`，144 张表归类完毕（启用 118、排除 26），修资料核对跨公司同步 | 拉最后一个分支同步编码规范「多租户规范」 |
| 2026-11-03 / Claude | `fix/tenant-modules-platform` 提交 `10d5ed74`；从它拉 `fix/tenant-rules-doc`，同步编码规范，补迁移撤销的租户条件；SEC-011 改为待合并 | 等用户确认合并到 `develop` |
| 2026-11-03 / Claude | 合入 `origin/develop`（含 v1.6.0），适配 Kevin 新增的表与定时任务，全量通过；准备 PR 到 `develop` | 用户已要求创建 PR 并合并 |
