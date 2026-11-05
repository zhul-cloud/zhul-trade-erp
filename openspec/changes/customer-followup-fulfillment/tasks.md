## 1. 分支与迁移

- [x] 1.1 从 `feat/customer-followup` 拉出 `feat/customer-followup-fulfillment`；用 `git log --oneline -1` 确认分支基于 9f6ede9 之后的提交
- [x] 1.2 编写 `V1.5.9__customer_followup_fulfillment.sql`，内容见 design「Migration Plan」第 1–4、6、7 步：唯一键改为 `uk_board_target`，`followup_item`、`customer_followup`、`sop_stage`、`sop_blocker`、`after_sales` 的新字段与索引，5 个系统参数。删除 `zhul_erp_test` 后启动测试，Flyway 迁移成功
- [x] 1.3 把两份 SOP Excel 的 F1–F6、A1–A6 和履约 / 售后卡点整理成 `V1.5.10__sop_fulfil_aftersales_content.sql`，F7 并入 A1，A7 不导入，`ref_days` 按规格默认值写入。用 SQL 核对 board 3、4 各 6 行，三个板块共 19 行都有 `ref_days`
- [x] 1.4 实体、常量同步新字段与码值：`TARGET_ORDER = 5`、`CLOSE_AFTER_SALES = 5`、`NEXT_AFTER_SALES = 4`、异常位、评价状态、满意度、参数键；`board` 加入 `TenantTables` 已有归类，无需新表。`TenantIsolationContractTest` 通过

## 2. 订单履约推导

- [x] 2.1 实现 `FulfilDeriver`：按型号进度推导 F1–F6，手动步骤按 `OrderProgress.rank` 归入前一个自动步骤，并排除售后补发、历史迁移、已完成和已取消的订单。单元测试覆盖：分两部分取最慢、库存直发跳过 F2/F3、直发货代跳过 F4、全部取消的型号不参与、手动步骤「清关中」归入 F6
- [x] 2.2 实现履约异常（采购超期、到货差异待处理、出库单撤回未重建），写入 `exception_flags`、`exception_since`，异常消除后清空。单元测试覆盖三种异常的出现和消除
- [x] 2.3 实现预计送达：识别快递承运商、按批次估算后取最晚一批、`eta_source = 2` 不被覆盖、`eta_first` 只写一次、出运单作废后清空。单元测试覆盖 DHL 和海运承运商、分批出运、业务员改日期后再次出运
- [x] 2.4 实现节点提醒（design D4）：判断节点、记跟进后同一节点不再覆盖、没有节点时 `next_action_date` 为空并生成系统说明文字。单元测试覆盖 5 个节点，以及「业务员记跟进 → 新节点到达 → 被替换」
- [x] 2.5 `FollowupSync.resync` 接入履约推导。到达预计送达日或订单已完成时，关闭履约行、生成售后行；`stage_since` 在阶段变化时更新。集成测试：订单创建 → 采购 → 入库 → 出库 → 出运 → 到达预计送达日，验证每一步的阶段和最后转入 A1；订单详情确认收货后直接进入 A2

## 3. 售后评价推导

- [x] 3.1 实现 `AfterSalesDeriver`，按 design D7 的优先级推导 A1–A6；A3/A4 的下一步取售后单，`next_action_source = 4`。单元测试覆盖：售后单新建、待客户确认、结案、撤销后回到 A1 或 A2，以及多张售后单并存
- [x] 3.2 实现售后结束的四种情况，以及结束后再登记售后单时重新打开。用 `CadenceTest` 风格的单元测试覆盖 A6 两次提醒后结束为「未评价」
- [x] 3.3 核对任务 `FollowupReconcileJob` 覆盖 board 3、4，包括到预计送达日转入、A6 到期结束，以及上线补生成规则（预计送达在 30 天内、未完成的已出运订单进 A1）。`BackgroundEntryTenantContractTest` 通过；集成测试验证核对任务补生成的数量

## 4. 单据事件接入

- [x] 4.1 `FollowupHooks.orderChanged(soId)`，并接入销售订单的创建、取消、部分取消和客户已收货。`FollowupHooksTest` 为每个入口写一条用例，并验证跟进侧抛异常时订单操作照常成功
- [x] 4.2 接入采购单下单和取消、供应商发货登记和作废、入库、到货差异、实拍任务。`FollowupHooksTest` 每个入口一条用例
- [x] 4.3 接入出库单创建和撤回，以及出运单出运、作废、调整货物。`FollowupHooksTest` 每个入口一条用例
- [x] 4.4 接入售后单新建、改待客户确认、结案、撤销、修改下一步；结案保存 `case_note`。`FollowupHooksTest` 每个入口一条用例；售后单契约测试覆盖案例沉淀不超过 500 字

## 5. 记一次跟进与接口

- [x] 5.1 `RecordFollowupRequest` 增加阶段字段，并做与阶段匹配的校验（`FOLLOWUP_STAGE_MISMATCH`、A1 还没收到必须填日期、A5 一般或不满意必须填原因）。`FollowupApiContractTest` 覆盖每个阶段的合法和非法提交
- [x] 5.2 `record` 按阶段更新跟进对象：履约阶段改预计送达；A1、A2、A5、A6 推进阶段或结束；「没回复」计入连续无回复次数。集成测试验证每种答案保存后的阶段和下一步
- [x] 5.3 `SalesOrderService.confirmReceivedByFollowup`：复用 `complete` 的校验，条件不满足时返回 false；操作日志记为「客户已收货（来自跟进记录）」；`RecordResultVO.orderCompleted`。测试覆盖所有型号已出运、有型号没出运、订单已完成三种情况
- [x] 5.4 `GET /items` 支持 board 3、4 的列字段（预计送达和依据、异常说明、售后单号和状态、签收天数、满意度）以及「有异常」筛选；`overview` 中 board 3、4 设为 `available` 并返回构成分组。契约测试覆盖
- [x] 5.5 `GET /items/{id}/parts`：按采购单分组的分部分进度，以及已告诉客户的节点。契约测试覆盖三部分订单（两张采购单加库存直发）
- [x] 5.6 `GET /team`：实现结果、按负责人、阶段、异常四块（design D10），数据权限和部门参数沿用现有逻辑，没有团队权限时返回 403。契约测试覆盖每个指标的口径、「需要关注」的判断、平均停留超过参考天数、异常排序和 `exceptions=all`
- [x] 5.7 作战卡按 board 读取 SOP 和卡点；`followup-extract` 和 `followup-reply` 扩展字段与意图（design D13）。`FollowupAiContractTest` 覆盖新字段透传和两个新意图，不调用真实模型

## 6. 前端

- [x] 6.1 `FlowCards`、`StageBar` 开放订单履约和售后评价（履约末尾为「有异常」）。用本地页面目测两个板块可以切换，计数与接口一致
- [x] 6.2 `FollowupTable` 增加两个板块的列配置；订单履约的行可展开分部分进度；A3/A4 行显示「看售后单」。对照原型 K10、K11、K12 下半部分走查
- [x] 6.3 作战卡支持两个板块：异常和问题的应对、「AI 写一条」的意图、「查看出运单」链接。对照 K10、K11 走查
- [x] 6.4 `RecordFollowupModal` 按阶段切换中间区块（履约、签收、使用、满意度、评价），底部显示保存后的效果，「有问题」保存后打开 `CreateAfterSalesModal`（新增 `defaults`）。对照 K12 走查，Jest 覆盖各阶段的必填校验
- [x] 6.5 新增 `TeamView` 和「我的 / 团队」切换，点数字回到列表并带上负责人筛选。对照 K13 走查；没有团队权限的账号看不到切换
- [x] 6.6 售后结案弹窗增加「案例沉淀」；客户详情跟进分区在链路上标出履约和售后，时间线显示新的系统节点。用本地页面目测
- [x] 6.7 `npm run lint`、`npx antd lint ./src`、`npm test` 全部通过

## 7. 验证与收尾

- [x] 7.1 后端全量测试通过：删除 `zhul_erp_test` 后运行 `mvn test`
- [x] 7.2 完整链路走查（walkthrough/admin123）：PI 收款 → 转订单 → 采购 → 入库 → 出库 → 出运 → 到预计送达日 → A1 有问题建售后单 → 结案 → A5 满意 → A6 已评价，每一步核对板块、阶段、今日待办和客户详情；再用团队视图核对计数
- [x] 7.3 更新 `docs/01-业务架构/客户全生命周期集成路线图-V1.0.md` 的第二期状态；PR 说明写明迁移版本 V1.5.9、V1.5.10 可回退，不新增 `.env` 配置项，并说明依赖第一期先合并
