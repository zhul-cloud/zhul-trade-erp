# BUS-087 资料任务 AI 生成在 AI 不可用时回调事务回滚、队列卡住

## 结论 / 下一步

已修复，提 PR 发往 develop。随下一个版本上线后：卡在「生成中」的记录会在 1 分钟内由定时补交收尾，排队记录在 AI 恢复后自动提交。上线前，生产 AI 需要先恢复至少一个可用的连接方式（额度问题与本缺陷无关，由用户处理）。

## 范围与负责人

| 字段 | 内容 |
| --- | --- |
| 负责人 / 协作者 | Ezio·Claude Code |
| 关联 ID / 原专题 | BUS-087 |
| 目标与影响范围 | 资料任务、营销任务的「AI 生成」排队（`ContentAiService`）；`AiTaskServiceImpl.createAndSubmit` 的事务规则。不含 AI 连接方式额度本身 |
| 起点 commit / 工作分支 | develop `b55dd03d`；`fix/content-ai-queue-unavailable` |
| OpenSpec | 缺陷修复，不新建变更；主规范 product/product-content「AI 生成资料与营销内容」补一条规则和一个场景 |
| PR / 目标分支 | 见台账 link |
| 合并证据 | 见台账（按 Git 推导） |
| 目标版本 / 环境 | v1.36.0 起；所有租户 |

## 实施与验证结果

现象（生产 v1.35.0，2026-11-18 只读核对）：
- 16:55 起，租户 1000 启用的三种连接方式都不可用：Claude API Key 和 OpenAI API Key 额度用完，OpenAI 平台订阅达到用量上限。
- 资料生成的失败结果回调返回 500（`UnexpectedRollbackException`）或锁等待超时；编排服务重投 3 次后放弃，任务要等 20 分钟超时扫描。
- 并发回调出现 `Deadlock found`。
- 1 条记录的 AI 任务已失败，但记录仍是「生成中」，占着并发名额（上限 3）。242 条排队。

根因：
1. `ContentAiService.submit()` 在回调事务里调用 `@Transactional` 的 `createAndSubmit`。AI 不可用时它抛出 `AiUnavailableException`，代码虽然捕获了，外层事务已被标记为只能回滚，提交时整体回滚。结果是：
   - 回调返回 500；
   - 超时扫描先把 AI 任务改为失败（单独提交），随后收尾记录的事务回滚，记录永久停在「生成中」。
2. `apply()` 先锁自己那条记录，`pump()` 再按 id 顺序锁全部排队、生成中记录。两个回调同时到达时加锁顺序交叉，互相死锁。

修复：
- `createAndSubmit` 加 `noRollbackFor`（未配置、暂不可用）。这两种情况在建任务之前就抛出，没有要回滚的写入，所以不再污染调用方的事务。
- `pump()` 先判断 AI 是否可用：不可用时排队的记录继续排队，不判失败。
- 发起、取消、AI 任务结束都先锁住排队集合（`lockOpen()`），再动单条记录，加锁顺序一致。
- `pump()` 发现 AI 任务已结束、记录却仍在生成中的，按任务结果收尾（兜底，修复已有的卡住数据）。
- 新增定时补交 `resumeQueued()`（每分钟）：对有排队或生成中记录的公司各跑一次 `pump()`，AI 恢复后自动接着生成。
- 规范：排队记录在 AI 暂不可用时继续排队，恢复后自动提交。

| 命令 / 操作与证据 | 环境、commit / 版本 | 预期 | 实际 | 结论 |
| --- | --- | --- | --- | --- |
| `ContentAiContractTest#createAndSubmit_unavailable_doesNotPoisonOuterTransaction`，修复前运行 | 本机测试库，`AiTaskServiceImpl` 未改 | 复现生产异常 | `UnexpectedRollbackException` | 复现 |
| `ContentAiContractTest`（6 个，含新增 2 个） | 本机测试库，本分支 | 通过：AI 不可用时失败结果照常保存、排队的继续排队；卡住的生成中记录自动收尾；AI 恢复后补交；外层事务照常提交 | 6/6 通过 | 通过 |
| `BackgroundEntryTenantContractTest`、`TenantIsolationContractTest`、`AiTaskServiceImplTest`、`ProductContentIntegrationTest` | 本机测试库，本分支 | 新定时任务在没有租户上下文时可执行；其他行为不变 | 1、8、14、51 个均通过 | 通过 |

未覆盖：并发回调不再死锁靠加锁顺序保证，没有写多线程测试（结果不稳定），上线后看生产日志不再出现 `Deadlock found`。

## 合并、部署与逐 ID 验收

待合并、发版后补充。
