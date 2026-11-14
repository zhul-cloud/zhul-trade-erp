# BUS-001 等 业务批次 H：引用检查、并发覆盖与读写口径（2026-11-11）

## 范围与负责人

| 字段 | 内容 |
| --- | --- |
| 负责人 / 协作者 | Claude（核实、实施与验证） |
| 关联 ID / 原专题 | BUS-001、BUS-013、BUS-014、BUS-015、BUS-017、BUS-020、BUS-021、BUS-022（[01-客户与商机](../../01-business/01-客户与商机.md)、[05-全量业务代码审查](../../01-business/05-全量业务代码审查.md)、[07-security/03](../../07-security/03-全量平台与商品代码审查.md)）；BUS-035 为本批实施中的新发现 |
| 目标与影响范围 | 只改后端：客户删除、商机编辑与阶段流转、询价分配、回价导入、工作台在途统计、部门维护、商品兼容说明、商品候选审核；MyBatis 一级缓存范围（全局配置） |
| 起点 commit / 工作分支 | `e7d8bda4`（develop）/ `fix/business-batch-h` |
| OpenSpec | 缺陷修复，按现行规格修正行为，不新开提案 |
| PR / 目标分支 | [#252](https://github.com/zhul-cloud/zhul-trade-erp/pull/252) → develop |
| 合并证据 | 2026-11-11 合并到 develop（`e0894ce7`）；合并前并入 develop 解决台账冲突，CI 6 项通过 |
| 目标版本 / 环境 | v1.20.0；生产 |

## 原因与实施

- **BUS-001 客户删除漏查单据引用**：引用检查只查两张询盘表。扩充为询盘、商机、报价、PI、销售订单、出库、出运、售后、客户余额与退款（未删除即算，作废 / 取消也算，客户可改为禁用）；客户自己的联系人、单证、跟进、背调不算。删除前先锁客户行（批量按 ID 顺序）。残留：新建单据时没有锁客户，删除与建单仍可能交错，概率极低，未处理。
- **BUS-013 商机编辑覆盖阶段**：编辑把读到的整条实体写回。改为只写来源渠道、阿里等级、首次接触日期、需求摘要；编辑、推进、结束、重开、创建询盘 / 报价自动推进、按客户自动推进 / 赢单都先用 `SELECT … FOR UPDATE` 锁行读最新值再判断。
- **BUS-014 同一任务被并发分给两人**：待分配状态是普通读。任务读取统一按 ID 顺序 `FOR UPDATE`，分配、推荐、规则、改派、追加比价共用；后到的请求等前者提交后按最新状态拒绝。
- **BUS-015 导入回价绕过已报客户只读**：只读检查只在在线保存里。移到所有回价写入必经的 `writeQuotes`（调用方已锁询盘单），本人导入、代导入与在线一致；导入被拒时整个请求回滚，不留导入记录。
- **BUS-017 工作台在途只算第一页**：工作台取发货列表第一页（每页最多 100 条）计数。新增 `countScopedInTransit`，与发货跟踪列表「在途」同口径（租户、未删除、采购员数据权限、在途），COUNT 全部在途与其中已过预计到货日的；采购主管「采购执行」与老板「风险提醒」共用。
- **BUS-020 部门维护绕过事业部不嵌套**：普通改上级只查循环与本部门层级。新增 `BusinessUnitService.checkMove`（被移动部门或其下级有事业部时，新上级链上不能有事业部）与 `checkDelete`（已有单据归属的事业部不能删除，与取消标记同规则）；同时修正移动时只按本部门算层级上限、下级层级不随之更新的问题。
- **BUS-021 兼容说明读写不一致**：中文说明存在主表与说明表两处，手工新建 / 修改只写主表，列表优先读说明表、营销包只读说明表。手工新建（含反向关系）与修改同步写说明表中文行。
- **BUS-022 候选批量通过用旧快照**：批量通过先判断后加锁、锁后不重读。改为先锁再按最新值判断。实施中发现原「lockById 后再 selectById」的写法在同一事务里会命中 MyBatis 一级缓存，拿到的仍是加锁前对象（见 BUS-035），候选服务的四处统一改为一步 `SELECT … FOR UPDATE` 读取，删去不再使用的 `ProductCandidateMapper.lockById`。
- **BUS-035（新发现）事务内加锁后按 ID 重读拿到旧对象**：MyBatis 默认会话级一级缓存，同一事务里先 `selectById`、再锁行、再 `selectById`，第二次直接返回缓存里的旧对象，「锁后重读」失效。全项目约 18 处「lock 后 selectById」写法受影响（加锁前是否已读过同一行因处而异）。`application.yml` 设 `mybatis-plus.configuration.local-cache-scope: statement`，一次消除这一类问题；代价是同一事务内重复查询会多访问一次数据库。

## 验证

每项都先撤掉修复跑新测试确认失败（证明测试抓得住缺陷），恢复修复后通过。

| 命令 / 操作与证据 | 环境、commit / 版本 | 预期 | 实际 | 结论 |
| --- | --- | --- | --- | --- |
| `CustomerApiContractTest#deleteCustomerReferencedByOrderOpportunityOrQuotation_isRejected` | 本机测试库 | 仅订单 / 商机 / 报价的客户单删、批删都拒绝；单据软删后可删 | 修复前单删返回 ok；修复后通过 | 通过 |
| `OpportunityApiContractTest#editWhileStageChanging_keepsNewStageAndReachedValid`（另一事务推进 S3 未提交时保存基本信息） | 同上 | 阶段 S3、曾有效 1、新摘要保存 | 修复前阶段被写回 S2；修复后通过 | 通过 |
| `SourcingContractTest#concurrentAssign_sameUnassignedTask_onlyOneWins` | 同上 | 后到分配被拒，只有 1 条有效分配 | 修复前两次都成功；修复后通过 | 通过 |
| `SourcingExcelContractTest#importConfirm_quotedItem_isReadOnlyLikeOnline` | 同上 | 本人导入、代导入都拒绝，不留回价与导入记录 | 修复前导入成功；修复后通过 | 通过 |
| `ReceivingContractTest#workbenchTransitCounts_coverAllPages`（101 张在途，唯一超期的排在最后） | 同上 | 在途 101、超期 1；列表首页确为 100 条且无超期 | 通过 | 通过 |
| `BusinessUnitContractTest#deptMaintenance_keepsBusinessUnitsFlat` | 同上 | 事业部互挂、带事业部的上级挪到事业部下都拒绝；普通部门移动后下级层级 +1；有单据的事业部删除被拒 | 修复前事业部互挂成功；修复后通过 | 通过 |
| `ProductEdgeCaseIntegrationTest#relationshipNote_manualAndImported_stayConsistent` | 同上 | 手工说明写进中文说明表；导入后修改、清空，列表与说明表一致 | 修复前说明表没有中文行；修复后通过 | 通过 |
| `CandidateBatchEditContractTest#batchApprove_whileRejectInFlight_skipsRejected` | 同上 | 驳回未提交时批量通过：跳过「已审核」，仍是驳回 | 修复前 approved=1；修复后通过 | 通过 |
| `CandidateBatchEditContractTest#sameTransactionReread_seesCommittedChange`（BUS-035） | 同上 | 读已提交事务里第二次 selectById 读到别人已提交的新状态 | 去掉配置时仍是旧值 1；加上后为 4 | 通过 |
| 各改动所在测试类整类运行（客户、商机、询价、导入、收货、事业部、商品、候选） | 同上 | 全部通过 | 全部通过 | 通过 |
| 后端全量 `mvn test` | 本机测试库，分支 `fix/business-batch-h` | 全部通过 | 1609 个，失败 0、错误 0、跳过 8（原有跳过） | 通过 |

## 合并、部署与逐 ID 验收

| ID | 修复 / 验收范围 | 部署与目标环境验收证据 | 验收人 / 日期 | 结论与剩余项 |
| --- | --- | --- | --- | --- |
| BUS-001 | 有订单 / 商机 / 报价等引用的客户不能删除 | v1.20.0 已部署（[#257](https://github.com/zhul-cloud/zhul-trade-erp/pull/257)；[生产发版](https://github.com/zhul-cloud/zhul-trade-erp/actions/runs/43119493452) 成功，2026-11-12）；本机验收通过（2026-11-14，见事件记录） | Ezio / 2026-11-14 | 已关闭 |
| BUS-013 | 编辑基本信息不改阶段与结束信息 | v1.20.0 已部署（[#257](https://github.com/zhul-cloud/zhul-trade-erp/pull/257)；[生产发版](https://github.com/zhul-cloud/zhul-trade-erp/actions/runs/43119493452) 成功，2026-11-12）；本机验收通过（2026-11-14，见事件记录） | Ezio / 2026-11-14 | 已关闭 |
| BUS-014 | 同一待分配任务并发分配只有一方成功 | v1.20.0 已部署（[#257](https://github.com/zhul-cloud/zhul-trade-erp/pull/257)；[生产发版](https://github.com/zhul-cloud/zhul-trade-erp/actions/runs/43119493452) 成功，2026-11-12）；本机验收通过（2026-11-14，见事件记录） | Ezio / 2026-11-14 | 已关闭 |
| BUS-015 | 已报客户型号导入回价被拒 | v1.20.0 已部署（[#257](https://github.com/zhul-cloud/zhul-trade-erp/pull/257)；[生产发版](https://github.com/zhul-cloud/zhul-trade-erp/actions/runs/43119493452) 成功，2026-11-12）；本机验收通过（2026-11-14，见事件记录） | Ezio / 2026-11-14 | 已关闭 |
| BUS-017 | 工作台在途、超期按全部单据统计 | v1.20.0 已部署（[#257](https://github.com/zhul-cloud/zhul-trade-erp/pull/257)；[生产发版](https://github.com/zhul-cloud/zhul-trade-erp/actions/runs/43119493452) 成功，2026-11-12）；本机验收通过（2026-11-14，见事件记录） | Ezio / 2026-11-14 | 已关闭 |
| BUS-020 | 部门维护不能形成事业部嵌套；有单据的事业部不能删除 | v1.20.0 已部署（[#257](https://github.com/zhul-cloud/zhul-trade-erp/pull/257)；[生产发版](https://github.com/zhul-cloud/zhul-trade-erp/actions/runs/43119493452) 成功，2026-11-12）；本机验收通过（2026-11-14，见事件记录） | Ezio / 2026-11-14 | 已关闭 |
| BUS-021 | 兼容说明在列表、营销包、导入与修改间一致 | v1.20.0 已部署（[#257](https://github.com/zhul-cloud/zhul-trade-erp/pull/257)；[生产发版](https://github.com/zhul-cloud/zhul-trade-erp/actions/runs/43119493452) 成功，2026-11-12）；本机验收通过（2026-11-14，见事件记录） | Ezio / 2026-11-14 | 已关闭 |
| BUS-022 | 批量通过不覆盖并发驳回 | v1.20.0 已部署（[#257](https://github.com/zhul-cloud/zhul-trade-erp/pull/257)；[生产发版](https://github.com/zhul-cloud/zhul-trade-erp/actions/runs/43119493452) 成功，2026-11-12）；本机验收通过（2026-11-14，见事件记录） | Ezio / 2026-11-14 | 已关闭 |
| BUS-035 | 事务内加锁后重读得到最新值 | v1.20.0 已部署（[#257](https://github.com/zhul-cloud/zhul-trade-erp/pull/257)；[生产发版](https://github.com/zhul-cloud/zhul-trade-erp/actions/runs/43119493452) 成功，2026-11-12）；本机验收通过（2026-11-14，见事件记录） | Ezio / 2026-11-14 | 已关闭 |

## 事件记录

| 日期 / 作者 | 事件与证据 | 下一步 / 交接 |
| --- | --- | --- |
| 2026-11-11 / Claude | 认领 8 项；逐项核实缺陷仍在，实施并补测试（修复前失败、修复后通过）；实施 BUS-022 时发现 BUS-035 并一并修复 | 全量测试后创建 PR |
| 2026-11-11 / Claude | 后端全量 1609 个通过，9 项改为待合并 | 创建 PR → develop |
| 2026-11-11 / Claude | #252 CI 通过并合并（`e0894ce7`），9 项改为已合并待发布 | 随下一个版本发布 |
| 2026-11-12 / Claude | 随 v1.20.0 上线（#257，发版运行 43119493452 成功），9 项改为已发布待验收 | 生产验收 |
| 2026-11-14 / Claude | 本机验收（用户确认本机与生产效果相同）：develop `6bdde1f7`（业务代码与生产 v1.22.1 一致）上后端完整 `mvn test` 1652 条、前端 `npm test` 340 条、AI 编排服务 139 条全部通过，含本记录各 ID 的回归测试。用户 Ezio 作为验收人确认关闭 BUS-001、BUS-013、BUS-014、BUS-015、BUS-017、BUS-020、BUS-021、BUS-022、BUS-035 | 无 |
