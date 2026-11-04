## 1. 迁移与模型

- [ ] 1.1 编写 `V1.5.11__customer_followup_account.sql`（design「Migration Plan」第 1 步），删除 `zhul_erp_test` 后启动测试，Flyway 迁移成功；回退语句在本地执行一次后再迁移回来
- [ ] 1.2 编写 `V1.5.12__sop_account_content.sql`，从《客户经营与复购 SOP》整理 G4–G7 的目标、该做的事、条件、示例。用 SQL 核对 board 5 有 4 行
- [ ] 1.3 实体、常量同步：`TARGET_CUSTOMER = 6`、`CLOSE_NOT_CUSTOMER = 6`、客户情况码值、`situation` 新位、参数键；`customer_annual_plan` 加入 `TenantTables` 私有清单。`TenantIsolationContractTest` 通过

## 2. 推导

- [ ] 2.1 实现 `PurchasePattern`（design D3）。单元测试覆盖：1 次、2 次、≥3 次（奇数和偶数个间隔）、7 天内合并、已取消和售后补发不计、迁移订单计入、品牌品类前 3
- [ ] 2.2 实现 `AccountCadence`（design D4、D5）：等级、预计采购、沉睡、季度回顾四种提醒取最早，以及暂停。单元测试覆盖每种提醒、多种同时存在取最早、暂停与恢复、业务员日期优先
- [ ] 2.3 实现 `AccountFollowupSync.resync`（design D1、D2），接入 `FollowupSync.resync` 的最后一步。集成测试：首单成交后进入 G4；新询盘进入 G5、询盘结束回 G4；战略客户 G7；勾新品类 G6、勾暂不相关回 G4；等级变为未成交时移出、恢复时重新打开
- [ ] 2.4 沉睡标记与激活记录：集成测试覆盖标为沉睡、新询盘激活并写系统记录
- [ ] 2.5 核对任务覆盖 board 5，首次运行为全部成交客户生成对象。`BackgroundEntryTenantContractTest` 通过；集成测试验证补生成数量

## 3. 记一次跟进与接口

- [ ] 3.1 `RecordFollowupRequest` 增加客户经营字段与校验（design D8）。`FollowupApiContractTest` 覆盖四种答案的合法与非法提交、其他板块传入时返回 `FOLLOWUP_STAGE_MISMATCH`
- [ ] 3.2 `record` 按答案更新对象：新需求置 G5、新品类置 G6、没有需求回 G4、没回复计数、季度回顾更新日期；记录写入 `due_date`。集成测试验证保存后的阶段和下一步
- [ ] 3.3 `GET /items` 支持 board 5 的列字段（近 12 个月成交、采购规律、上次联系与是否超间隔、经营建议）和「沉睡」「全部老客户」分组；`overview` 中 board 5 设为 `available`。契约测试覆盖
- [ ] 3.4 作战卡接口返回 G1–G3 结果、交叉销售建议（design D6）、战略客户与年度计划。契约测试覆盖交叉销售的推荐和不足 2 个客户不推荐
- [ ] 3.5 战略客户与年度计划接口（design D7）：设置 / 取消、保存当年计划、进度计算。契约测试覆盖权限、字段长度、金额精度、进度百分比 HALF_UP
- [ ] 3.6 团队视图经营指标（design D9）。契约测试覆盖每个指标的口径
- [ ] 3.7 AI：`followup-reply` 新意图 `account_touch`、`followup-extract` 新客户情况取值（design D10）。`FollowupAiContractTest` 与编排服务 `test_server` 覆盖，不调用真实模型

## 4. 前端

- [ ] 4.1 `FlowCards`、`StageBar` 开放客户经营（G4–G7、沉睡、培育中、全部老客户）。本地目测计数与接口一致
- [ ] 4.2 `FollowupTable` 增加 board 5 列配置和「建询盘」操作。对照原型 K14 走查
- [ ] 4.3 作战卡：G1–G3 结果、触达理由、交叉销售建议、战略客户与 `AnnualPlanModal`。对照 K14、K16 走查
- [ ] 4.4 `RecordFollowupModal` 客户经营版问题，「有新需求」保存后打开 `NewInquiryModal`。对照 K15 走查；Jest 覆盖必填校验
- [ ] 4.5 `TeamView` 经营指标与按负责人一列。对照 K17 走查
- [ ] 4.6 `npm run lint`、`npx antd lint ./src`、`npm test` 全部通过

## 5. 验证与收尾

- [ ] 5.1 后端全量测试通过：删除 `zhul_erp_test` 后运行 `mvn test`
- [ ] 5.2 本地走查（walkthrough/admin123）：成交客户出现在 G4，提醒日期按等级；建询盘后进 G5 并暂停提醒；设战略客户填年度计划；勾新品类进 G6；把一个客户的最近订单日期改到 200 天前（仅本地）后核对，出现在沉睡；团队视图核对计数
- [ ] 5.3 更新路线图第三期状态；PR 说明写明迁移 V1.5.11、V1.5.12 可回退，不新增 `.env` 配置项，AI 编排服务需重新构建，依赖第二期先合并
