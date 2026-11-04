## 1. 迁移与模型

- [ ] 1.1 编写 `V1.5.13__customer_followup_develop.sql`（design「Migration Plan」第 1 步），删除 `zhul_erp_test` 后启动测试，Flyway 迁移成功；回退语句在本地执行一次后再迁移回来
- [ ] 1.2 编写 `V1.5.14__sop_develop_content.sql`，从《客户主动开发 SOP》整理 D1–D7 的目标、该做的事、条件、示例和序列节点建议。用 SQL 核对 board 1 有 7 行且都有 `ref_days`
- [ ] 1.3 实体、常量同步：结束原因 7、8、9，触达结果码值，`situation` 新位，来源方式，参数键。`TenantIsolationContractTest` 通过

## 2. 推导

- [ ] 2.1 实现 `DevelopStageDeriver`（design D4）。单元测试覆盖 D1–D7 每个阶段、背调中、资格不足、只进不退、主联系人字段算可联系
- [ ] 2.2 实现 `DevelopCadence`（design D5）。单元测试覆盖每个阶段的提醒、序列第 4 / 9 / 16 天、序列发完建议转培育、业务员日期优先
- [ ] 2.3 实现 `DevelopFollowupSync`，接入 `FollowupSync.resync`（排在客户经营之前），含自动结束（design D8）。集成测试：新建 → D1 → 背调完成 → D4 → 触达 → D5 → 回复 → D6；客户自己发来询盘后开发记录结束
- [ ] 2.4 核对任务覆盖 board 1，不补生成。`BackgroundEntryTenantContractTest` 通过

## 3. 接口

- [ ] 3.1 `DevelopEligibility` 与新建开发对象接口（design D2、D3），含查重返回和不建联系人分支。契约测试覆盖：新建、疑似重复、仍然新建、成交客户不能加入、已有进行中的开发记录
- [ ] 3.2 加入开发、重新开发接口。契约测试覆盖条件不满足的每种原因
- [ ] 3.3 记跟进的触达结果（design D6）。`FollowupApiContractTest` 覆盖五种结果的合法与非法提交，以及保存后的计数、日期和阶段
- [ ] 3.4 `OpportunityService.createForCustomer` 与转商机接口（design D7）。测试覆盖：成功转出后商机成交出现商机行、已有进行中商机时拒绝、权限不足
- [ ] 3.5 停止开发接口：原因必填、「其他」必须填说明。契约测试覆盖
- [ ] 3.6 `GET /items` 支持 board 1 的列字段、三组导航和排序（design D9）；`overview` 中 board 1 设为 `available`；作战卡返回背调结论、联系人、序列进度。契约测试覆盖
- [ ] 3.7 团队视图开发指标（design D10）。契约测试覆盖每个指标的口径
- [ ] 3.8 AI：`cold_outreach` 意图与新的客户情况取值（design D11）。`FollowupAiContractTest` 与编排服务 `test_server` 覆盖，不调用真实模型

## 4. 前端

- [ ] 4.1 `FlowCards`、`StageBar` 开放主动开发。本地目测计数与接口一致
- [ ] 4.2 `FollowupTable` board 1 列配置与操作；`NewProspectModal`（含查重）。对照原型 K18、K19 走查
- [ ] 4.3 `DevelopCard` 作战卡、`ConvertOpportunityModal`、`StopDevelopModal`。对照 K18、K19 走查
- [ ] 4.4 `RecordFollowupModal` 主动开发版，「明确拒绝」保存后打开停止开发。Jest 覆盖必填校验
- [ ] 4.5 客户详情跟进分区的「加入主动开发 / 重新开发 / 开发阶段」。本地目测
- [ ] 4.6 `TeamView` 开发指标。对照 K17 走查
- [ ] 4.7 `npm run lint`、`npx antd lint ./src`、`npm test` 全部通过

## 5. 验证与收尾

- [ ] 5.1 后端全量测试通过：删除 `zhul_erp_test` 后运行 `mvn test`
- [ ] 5.2 本地走查（walkthrough/admin123）：新建开发对象 → 背调（本机不跑真实 AI，手动设潜力星级代替）→ 加联系人 → 首次触达 → 序列提醒（本地改首次触达日验证第 9 天）→ 回复 → 采购信号 → 转商机，商机出现在商机成交；另一个对象明确拒绝并停止，再从客户详情重新开发；团队视图核对计数
- [ ] 5.3 更新路线图第四期状态；PR 说明写明迁移 V1.5.13、V1.5.14 可回退，不新增 `.env` 配置项，AI 编排服务需重新构建，依赖第三期先合并
