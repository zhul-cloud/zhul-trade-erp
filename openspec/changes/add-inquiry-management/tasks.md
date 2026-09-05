## 1. 数据库建表

- [x] 1.1 按 `.claude/rules/coding.md` 建表规范编写 `customer`、`supplier` 建表 SQL（含审计字段、`tenant_id`+`deleted_at`、索引），追加到新的 `sql/build/sql/schema_v1.1.sql`，并通过本地 MySQL 8 执行校验无报错
- [x] 1.2 编写 `customer_inquiry` 建表 SQL（含 `inquiry_code` 唯一索引、`status`/`create_time`/`deleted_at` 索引），执行校验无报错
- [x] 1.3 编写 `inquiry_order`、`inquiry_order_item` 建表 SQL（含 `customer_inquiry_id`/`assignee_id`/`inquiry_order_id` 索引），执行校验无报错
- [x] 1.4 编写 `inquiry_order_supplier` 建表 SQL，字段含 `source_type`、`supplier_id`（可空）、`channel_platform`、`channel_name`、`channel_link`（design.md 决策11），执行校验无报错
- [x] 1.5 编写 `inquiry_order_item_quote` 建表 SQL，字段含 `inquiry_order_supplier_id`（不直接存 `supplier_id`）、金额三元组、`quote_status`，执行校验无报错
- [x] 1.6 编写通用 `ai_task` 建表 SQL（`skill_id`/`status`/`output` JSON/`error_message`/`requested_by`/时间戳），执行校验无报错
- [x] 1.7 更新 `.claude/context/data-model.md`，把这 8 张表从"后续版本规划"补充为已落地表清单

## 2. 后端 — 主数据模块（modules/masterdata）

- [x] 2.1 实现 Customer 的 Entity(DO)/DTO/VO/Repository（MyBatis）/Service/Controller 三层结构，提供创建接口（仅名称必填），单元测试覆盖"仅名称创建成功"和"名称为空拒绝"两个场景（对应 `specs/master-data/customer/spec.md`）
- [x] 2.2 实现 Customer 内联快速创建接口与同租户名称重复提示逻辑，单元测试覆盖重复名称场景
- [x] 2.3 实现 Customer 软删除（`deleted_at`），单元测试验证删除后记录不出现在正常查询中但历史引用不受影响
- [x] 2.4 实现 Supplier 的 Entity/DTO/VO/Repository/Service/Controller，创建、内联快速创建、同租户名称重复提示、软删除，复用 2.1-2.3 的实现模式，单元测试覆盖同等场景（对应 `specs/master-data/supplier/spec.md`）
- [x] 2.5 实现"从电商询价渠道创建供应商"接口：接收 `channel_name` 预填供应商名称，单元测试覆盖预填与名称重复提示

## 3. 后端 — AI 任务编排模块（modules/aitask）

- [x] 3.1 实现 `ai_task` 的 Entity/DTO/VO/Repository/Service/Controller，提供创建任务接口（指定 `skill_id`+输入数据，初始状态"排队中"），单元测试覆盖创建场景
- [x] 3.2 确定并实现 webhook 回调鉴权机制（design.md Open Questions：共享密钥或内网信任二选一），`SecurityConfig` 白名单加入回调端点且不要求用户态 JWT，编写鉴权失败返回 401/403 的测试
- [x] 3.3 实现向 AI 编排服务提交任务的 HTTP 客户端封装（`POST /skills/{skill_id}/run`，携带 `callback_url`，同步只处理 202 受理响应，不阻塞等待结果）
- [x] 3.4 实现 webhook 回调接口：解析 `status`/`output`/`error`，更新对应 `ai_task` 状态为"已完成"或"失败"并记录时间，单元测试覆盖成功/失败两种回调
- [x] 3.5 实现按 `skill_id` 路由到业务处理器的轻量分发层（策略模式或类似机制），先接入 `inquiry-parse-and-split` 一个处理器，验证新增处理器时无需修改路由分发代码本身 —— **范围调整**：本次只搭好分发框架本身 + 一个 `noop-test` 占位处理器用于验证路由机制（`inquiry_order`/`customer_inquiry` 模块此时还不存在，无法实现一个有实际业务效果的 `inquiry-parse-and-split` 处理器）；该 skillId 的真实处理器仍按任务 4.4 单独实现，不受此调整影响
- [x] 3.6 搭建一个本地开发用的 AI 编排服务 stub（简单 HTTP 服务，收到 `/skills/inquiry-parse-and-split/run` 后延迟回调固定的示例拆单 JSON），用于后续联调，不计入生产部署范围（design.md Non-Goals 明确 AI 编排服务本身不在本次实现范围）

## 4. 后端 — 询盘模块 · 客户询盘（modules/inquiry）

- [x] 4.1 实现客户询盘编号生成器：`IQ{YYYYMMDD}{NNN}`，查询当日最大流水号+1，查询失败时随机数兜底并标记备注待核实（design.md 决策9），单元测试覆盖正常生成与失败兜底两种路径
- [x] 4.2 实现客户询盘 Entity/DTO/VO/Repository/Service/Controller 与提交接口：创建后状态固定为"待解析"，不创建 `ai_task`，单元测试覆盖"提交后不自动触发解析"（对应 `specs/inquiry/customer-inquiry/spec.md`）
- [x] 4.3 实现"开始 AI 解析"接口：仅"待解析"状态可调用，创建 `ai_task` 并通过 3.3 的客户端提交，成功后置客户询盘状态为"解析中"，单元测试覆盖非"待解析"状态拒绝调用
- [x] 4.4 实现 `inquiry-parse-and-split` 的 webhook 结果处理器（对接 3.5 的路由层）：成功时把 `ai_task.output` 写入、客户询盘状态置"待确认"；失败时状态置"解析失败"并记录 `error_message`，单元测试覆盖两条路径
- [x] 4.5 实现拆单预览查询接口：直接读取 `ai_task.output` 渲染，不落地 `inquiry_order`；实现"确认拆单"接口：按提交的（可能已编辑）分组创建 `inquiry_order`+`inquiry_order_item`（空分组跳过），客户询盘状态置"已确认"，集成测试覆盖多分组批量生成与空分组跳过两个场景
- [x] 4.6 实现客户询盘后续状态人工流转接口（待报价→报价中→已报价→已成交/已取消），非法状态跳转拒绝，集成测试覆盖合法与非法转换（对应 `.claude/rules/coding.md` BCDE 原则中的 Design 用例）

## 5. 后端 — 询盘模块 · 询盘单（modules/inquiry）

- [x] 5.1 实现询盘单/明细编号生成器（`{客户询盘编号}{字母}`、无父级时 `IQ{YYYYMMDD}{NNN}{字母}`、明细 `{询盘单编号}{NN}`，字母超 26 后 AA/AB 顺延），单元测试覆盖三种编号场景
- [x] 5.2 实现询盘单手动创建接口（`customer_inquiry_id`/`ai_task_id` 恒为空，选择的客户直接写入独立的 `customer_id` 字段而非借道 `customer_inquiry`，无置信度字段，初始状态"待分配"，编号统一走无父级独立编号规则），单元测试覆盖对应 `specs/inquiry/inquiry-order/spec.md` 的手动创建场景（含选客户/不选客户两种）
- [x] 5.3 实现分配/重新分配采购员接口（写入 `assignee_id`，"待分配→已分配"，重新分配覆盖不留历史），单元测试覆盖首次分配与重新分配
- [x] 5.4 实现 `inquiry_order_supplier` 关联接口，按 `source_type` 做条件校验（正式供应商必须 `supplier_id`，电商询价必须 `channel_platform`+`channel_name`），应用层校验、不使用数据库 CHECK 约束，单元测试覆盖两种来源类型及电商询价缺店铺名拒绝的场景
- [x] 5.5 实现"转为正式供应商"接口：联动 2.5 的供应商创建，成功后更新该关联记录 `source_type`→1、回填 `supplier_id`，`channel_*` 字段保留，集成测试验证历史报价数据不受影响
- [x] 5.6 实现 `inquiry_order_item_quote` 报价录入接口：按 `finance-rules.md` 规则计算本位币金额（HALF_UP 舍入到 2 位小数，汇率存 6 位小数），汇率未维护时允许保存为"待报价"且本位币金额留空，单元测试覆盖零值/多币种/汇率缺失边界（`.claude/rules/coding.md` BCDE 原则要求财务计算 100% 覆盖率）
- [x] 5.7 实现报价对比查询接口：按型号聚合各报价来源（正式供应商/电商询价渠道统一通过 `inquiry_order_supplier_id` 取来源展示名），标记本位币最低价，单元测试覆盖多来源比价与部分未报价的场景
- [x] 5.8 实现询盘单状态人工流转接口（已分配→已发供应商→已收报价→已报客户→已成交/已取消），不依据供应商子状态自动聚合，集成测试覆盖部分供应商回复不触发自动变更
- [x] 5.9 实现询价话术/邮件模版只读查询接口（数据来自 `ai_task.output` 或手动创建询盘单时的空值），不提供编辑接口

## 6. 前端 — 主数据内联组件

- [x] 6.1 实现客户快速创建弹窗组件（M03：名称必填+选填字段），复用于客户询盘录入场景，遵循 `.claude/context/ui-design-patterns.md` 弹窗结构规范
- [x] 6.2 实现供应商快速创建弹窗组件（M04：名称必填+选填字段+主营品牌），复用于询盘单添加正式供应商、以及"转为正式供应商"（预填 `channel_name`）两个场景

## 7. 前端 — 客户询盘模块页面

- [x] 7.1 实现 P01 客户询盘列表页：统计卡片行、多行网格筛选卡片（对照 `docs/03-产品原型/03-业务域/00-询盘中心/00-询盘单/询盘单.op` 及 `询盘单-客户询盘列表页.png`）、状态胶囊配色、表格
- [x] 7.2 实现 P02 新建客户询盘弹窗：文本/Excel/图片三种录入方式 Tab、客户选择（含内联新建）、提交后跳转 P03"待解析"态
- [x] 7.3 实现 P03 客户询盘详情页五种状态展示（待解析/解析中/待确认拆单预览/已确认/解析失败），"开始AI解析"按钮、拆单预览可编辑表格与低置信度高亮、"确认拆单"/"取消询盘"操作，关联询盘单表格含"查看"操作列跳转 P05
- [x] 7.4 在浏览器中走通客户询盘录入→（mock AI 服务）解析→预览编辑→确认拆单全流程，验证页面状态与后端接口返回一致 —— 用 `gstack browse` headless Chromium 实际登录（admin/admin123）走完整链路：P01列表→点击"新建客户询盘"→P02填写客户(内联搜索选中)+原始内容→提交后跳转P03"待解析"态（文案/按钮与设计一致）→点击"开始AI解析"→"解析中"loading态→用真实 webhook 回调（`X-AI-Callback-Token`鉴权）模拟AI服务返回2组拆单结果→刷新页面进入"待确认"拆单预览态（分组卡片、置信度标签、待核实行黄色高亮均正确渲染）→点击"确认拆单"→成功生成2条"待分配"询盘单、页面切换到"已确认"态并展示关联询盘单列表。全程真实前后端交互，非mock。发现的非阻塞问题：antd v6 若干废弃属性告警(`destroyOnClose`/`maskClosable`/`dropdownRender`/静态`message`)；"已确认"态关联询盘单表格用编号本身做链接、未做独立的"操作/查看"列（与原型图有细节出入但功能等价）；P01统计卡片为近似值（无后端聚合接口，已在7.1中说明）

## 8. 前端 — 询盘单模块页面

- [x] 8.1 实现 P04 询盘单列表页：统计卡片行、多行网格筛选卡片、"我的待处理"筛选 tab、未分配行"分配"按钮高亮 —— 统计卡片沿用 P01 的近似方案（`pageSize=1` 分页取 total，无专门聚合接口）
- [x] 8.2 实现 P05 询盘单详情页三个 Tab：型号明细、供应商与报价（含正式供应商/电商询价两种卡片样式、报价对比表格最低价高亮、"转为正式供应商"入口）、询价话术（只读+复制）
- [x] 8.3 实现 P06 手动新建询盘单弹窗：客户可选、品牌品类必填、型号明细动态增减表格（无置信度列）—— 同时支持从 P03「解析失败」态跳转过来的入口B（关联已存在的 customer_inquiry，见后端改动说明）
- [x] 8.4 实现 M01 分配采购员弹窗、M02 添加供应商/录入报价弹窗（"正式供应商"/"电商询价"分段控件切换字段）
- [x] 8.5 浏览器+curl 双重验证："手动创建(入口A选客户/入口B关联已有客户询盘)→分配采购员→添加正式供应商+电商询价渠道各一个→录入两条报价→报价对比确认最低价标记正确（HALF_UP精度）→转为正式供应商→报价对比确认历史报价保留"全链路真实跑通，细节见完成报告

## 9. 联调与验收

- [x] 9.1 使用 3.6 的本地 AI 服务 stub，端到端验证"提交客户询盘→手动开始解析→webhook 回调→预览确认→生成询盘单"完整链路 —— 实际启动 `scripts/ai-orchestrator-stub/server.py` 跑通全链路时发现并修复了一个真实 bug：stub 脚本的示例输出用的是 `original_model`/`confirmed_model`/`correction_note`（下划线命名），但 `AiParseItemDTO`/`AiParseGroupDTO` 是标准 Java 驼峰命名（`originalModel` 等），且完全没有配置 Jackson 蛇形命名策略，导致这些字段静默丢失为 null（之前几轮"验证"用的都是手写 camelCase 的 curl payload，从未真正跑过这个脚本，没暴露这个问题）；同时 stub 的示例数据完全没有 `inquiryTemplate`/`emailTemplateCn`/`emailTemplateEn` 三个模版字段。已修正 `server.py`：字段改成驼峰命名，补全三个模版字段。修正后重新跑通全链路（真实启动 stub + 真实 webhook 回调，非手写curl模拟），确认 `customer_inquiry` 正确进入待确认态、`preview`接口型号信息与模版均正确渲染，`confirm-split` 后生成的3张 `inquiry_order` 均携带正确的话术/邮件模版内容
- [x] 9.2 验证"分配"操作默认对所有登录用户开放、不做角色限制（PRD 默认假设 4），确认接口未遗留未实现的角色校验分支 —— 检查 `masterdata`/`aitask`/`inquiry` 三个模块全部 Controller，均无 `@PreAuthorize`/`@perm` 等权限注解；`SecurityConfig` 白名单只包含 AI 回调端点，其余（含分配接口）走 `anyRequest().authenticated()`，即"任意已登录用户可调用"，符合默认假设
- [x] 9.3 跑通 `.claude/rules/coding.md` 覆盖率要求：财务计算（报价金额换算）与状态流转相关代码测试覆盖率达到 100%，模块整体语句覆盖率 ≥ 70% —— 项目原本未接入覆盖率工具，本次给 `zhul-erp-backend/pom.xml` 加了 `jacoco-maven-plugin`（0.8.12）。首次测出的真实数据暴露了缺口：`InquiryOrderServiceImpl.advanceStatus`/`getQuoteComparison`、`CustomerInquiryServiceImpl.cancel`/`advanceStatus`、`InquiryCodeGenerator` 的两个询盘单编号兜底分支均有未覆盖行；`CustomerInquiryServiceImpl.page`（列表查询）完全 0% 覆盖。补充 11 个单测（88个测试全部通过）后：`recordQuote`/`calculateCnyAmount`（报价金额换算）与两个模块的 `advanceStatus`/`cancel`（状态流转）方法行覆盖率均为 100%；`inquiry`+`masterdata`+`aitask` 三个新模块整体行覆盖率 72.2%（服务层单独看 82.0%），达标。（注：项目其余 v1.0.0 既有模块此前完全没有测试，"整体覆盖率"按本次新增模块自身计算，不包含既有代码）
- [x] 9.4 运行 `openspec validate add-inquiry-management --strict` 确认变更仍然合法，作为合并前的最后检查 —— 通过（`Change 'add-inquiry-management' is valid`）

## 10. 可选 / 后续（不阻塞本次验收）

- [ ] 10.1 （可选）工作台"待办事项"接入"待解析"/"待确认"/"待分配"提醒（design.md Risks 与 PRD 默认假设 7 均标注为建议但不阻塞）

## 11. 上线后修复（真实使用中发现，design.md 决策13）

- [x] 11.1 修复 `AiTaskServiceImpl.submit()` 提交阶段失败时不通知业务层的 bug：AI 编排服务提交阶段就失败（如连接被拒绝）会正确标记 `ai_task` 为失败，但从未调用 `resultDispatcher.dispatch()`，导致 `customer_inquiry` 永远停留在"解析中"。已修复为和 webhook 回调失败走同一条通知路径，新增单测覆盖
- [x] 11.2 新增 `AiTaskTimeoutWatchdog` 定时任务：任务停留"排队中/处理中"超过阈值（默认5分钟，`zhul.ai-task.timeout-minutes` 可配置）且未收到任何回调，标记失败并走 11.1 的统一分发路径——补齐 PRD 第7节"AI 处理超时（无回调）"当初写好但未实现的兜底规则，新增单测覆盖
- [x] 11.3 客户询盘"解析失败"态新增"重试"按钮（`POST /{id}/retry-parse`）：复用已保存的原始内容重新发起一次 AI 解析，状态转回"解析中"；仅"解析失败"状态可调用，新增单测覆盖首次重试成功与非法状态调用两种场景

验证：`mvn test` 93/93 通过；对真实卡住的记录（IQ20260904006）用重试接口验证了完整链路——从"解析失败"重试到 webhook 回调成功、进入"待确认"态，型号数据正确。

## 12. 接入真实 AI 编排服务（design.md 决策14，超出原 Non-Goals 范围，用户明确要求后追加）

- [x] 12.1 新增 `scripts/ai-orchestrator/`：真实实现（区别于 `scripts/ai-orchestrator-stub` 占位脚本），把"烛龙询盘助手"的 `inquiry-parser`、`order-splitter` 两个 skill 的 `SKILL.md` 复制进 `skills/` 子目录（符合 design.md 决策2"skill 与服务代码同仓库同提交"），用 `claude -p`（Claude Code 非交互模式，走当前账号 OAuth 登录态）+ `--json-schema` 输出符合后端字段约定的结构化 JSON，`--allowedTools WebSearch` + `--permission-prompts none` 精确放行联网搜索、其余工具自动拒绝
- [x] 12.2 调大 `AiTaskTimeoutWatchdog`（11.2）的默认超时阈值从5分钟到20分钟，匹配真实AI处理的分钟级耗时，避免误判超时；同步更新 `application.yml` 注释和 PRD 相应描述
- [x] 12.3 用 `--max-budget-usd`（默认 $5，`AI_ORCHESTRATOR_MAX_BUDGET_USD` 可配）给单次调用设费用上限，写入 README 明确告知这是真实付费调用，`scripts/ai-orchestrator-stub` 保留不动供不想产生费用时切换回去联调

验证：实测3次真实调用（2组不同型号组合），确认联网搜索确实真实发生（`modelUsage.<model>.webSearchRequests` 非零）、输出结构与后端 `AiParseGroupDTO`/`AiParseItemDTO` 契约完全匹配、grouping/模版内容与"烛龙询盘助手"原 skill 设计风格一致；单次2型号调用费用约 $0.28～$0.34，耗时 45～70 秒。

## 13. 修复"提交事务未提交、回调已经打回来"的竞态条件（真实使用中发现）

- [x] 13.1 修复 `AiTaskServiceImpl.submit()`：`create()` 插入的 `ai_task` 还在 `createAndSubmit()` 所在事务里、尚未提交，`submit()` 却立刻发起异步 HTTP 提交——如果 AI 编排服务响应快到回调能在事务提交前打回来，`handleCallback()` 用另一个数据库连接查不到这条未提交的记录，报"AI任务不存在"，这条回调直接丢失，只能等 `AiTaskTimeoutWatchdog`（决策13）几十分钟后兜底，报出一个跟真实失败原因完全无关的"处理超时"提示。真实复现场景：客户询盘走图片/Excel录入、`raw_content` 为空，编排服务同步立即拒绝，稳定触发竞态；用 Claude Code 联网搜索走真实解析流程耗时几十秒到几分钟，不会撞见这个时间窗口，所以此前的真实调用测试都没暴露这个问题。已修复为用 `TransactionSynchronizationManager.registerSynchronization(...).afterCommit()` 把提交动作推迟到事务真正提交之后，新增单测模拟事务上下文验证"提交前不调用、提交后才调用"
- [x] 13.2 用真实卡住的记录（`IQ20260904011`，图片输入、`raw_content`为空）验证修复：重试后3秒内正确进入"解析失败"并报出准确原因（"rawContent 为空，本地编排服务暂不支持图片/Excel附件解析"），不再需要等 20 分钟、也不再显示无关的超时提示

验证：`mvn test` 94/94 通过（含新增的竞态复现单测）；真实记录重试验证符合预期。**顺带确认一个已知的既有缺口**（不是本次引入，之前实现阶段就已如此）：P02"图片上传"入口目前没有真实的文件存储后端，`raw_content` 恒为空，图片类客户询盘现阶段无法真正解析，只能得到"暂不支持"的明确失败提示——这是功能未完成，不是这次修复的bug。
