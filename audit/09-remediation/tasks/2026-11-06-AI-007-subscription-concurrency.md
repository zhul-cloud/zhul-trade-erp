# AI-007 个人订阅方式取消单任务限制，与 API 方式一致并发执行

## 范围与负责人

| 字段 | 内容 |
| --- | --- |
| 负责人 / 协作者 | Claude（实施与验证）；用户 Ezio 提出并验收 |
| 关联 ID / 原专题 | [AI-007](../../04-ai-and-operations/01-AI任务可靠性与诊断.md) |
| 目标与影响范围 | AI 编排服务的订阅方式：去掉「同时只执行一个、忙碌即失败」的限制。只改编排服务，后端、前端、数据库、配置都不变 |
| 起点 commit / 工作分支 | develop `c0a23219`；分支 `fix/ai-007-subscription-concurrency` |
| OpenSpec | 改的是现有规格的一条要求，直接更新主规格 [model-providers](../../../openspec/specs/ai-platform/model-providers/spec.md)「订阅任务隔离与完整结果」，并新增场景「多个订阅任务同时执行」 |
| PR / 目标分支 | [#194](https://github.com/zhul-cloud/zhul-trade-erp/pull/194)，发往 `develop`，CI 通过后自动合并 |
| 合并证据 | `develop`：#194 `8a5fc1c4` |
| 目标版本 / 环境 | v1.9.1（2026-11-06 发布）；原写「随下一版发布」；生效需要重建 AI 编排服务镜像 |

## 实施与验证结果

- **根因：** [`subscription.py`](../../../scripts/ai-orchestrator/subscription.py) 用进程内信号量（容量 1）包住整个订阅调用，拿不到就直接报错；API 方式没有这个限制。
- **修改：**
  - 删除信号量及相关的 `threading` 导入，`call_subscription` 不再检查是否忙碌；
  - 每个任务仍使用独立的临时工作目录、隔离环境变量和进程组，超时与退出时清理子进程，这些都不变；
  - 同步更新规格、[README](../../../scripts/ai-orchestrator/README.md) 和[部署文档](../../../docs/06-部署运维/04-AI编排服务.md)。

| 命令 / 操作与证据 | 环境、commit / 版本 | 预期 | 实际 | 结论 |
| --- | --- | --- | --- | --- |
| [`test_subscription.py`](../../../scripts/ai-orchestrator/test_subscription.py) `test_concurrent_tasks_run_in_parallel_with_own_workdir`：两个线程同时调用 `call_subscription`，模拟的 CLI 要等两个任务都进入调用后才返回 | 本机 Windows，Python 3.13 venv，按 `requirements.txt` 安装 | 两个任务都成功，工作目录互不相同 | 修复前：失败，第二个任务报「个人订阅正在处理其他任务」，第一个等待超时；修复后：通过 | 通过（缺陷已复现并修复） |
| 编排服务全部 Python 测试（`test_subscription`、`test_server`、`test_job_deadline`、`test_diagnostics`、`test_claude_stream`） | 同上 | 全部通过 | 97 个用例，跳过 3 个 Linux 专用用例；多数运行全部通过 | 通过（见下条说明） |
| 偶发失败：`test_server.HttpTokenTest.test_missing_or_wrong_token_rejected` | 同上，切回 develop 原代码对照 | — | 原代码同样偶发失败（8 次中 3 次）：Windows 下服务端返回 401 时没读完请求体，连接被中止（WinError 10053）。与本次改动无关 | 已有问题，单独提出 |
| `openspec validate --specs` | 本机 | 规格校验通过 | 82 项通过 | 通过 |

- **未覆盖：**
  - 没有用真实订阅账号同时跑多个任务：本机没有登录订阅，订阅额度和并发上限取决于账号；
  - Linux 专用的进程组用例在 Windows 上跳过，要在 Docker / CI 的 Linux 环境中运行。

## 合并、部署与逐 ID 验收

| ID | 修复 / 验收范围 | 部署与目标环境验收证据 | 验收人 / 日期 | 结论与剩余项 |
| --- | --- | --- | --- | --- |
| AI-007 | 订阅方式同时提交多个任务都能执行，不再报「正在处理其他任务」 | v1.9.1 已部署（[#197](https://github.com/zhul-cloud/zhul-trade-erp/pull/197) `e5487fbd`，生产发版运行 42210502306 成功，健康检查通过，2026-11-06） | — | 已发布待验收：待生产验收 |

## 阻塞与下一步

- 合并后随下一版发布；需要重建 AI 编排服务镜像，订阅登录卷保留，不需要重新登录。
- 发布后同时提交两个订阅任务验收，并观察订阅额度消耗与容器内存。

## 事件记录

| 日期 / 作者 | 事件与证据 | 下一步 / 交接 |
| --- | --- | --- |
| 2026-11-06 / Claude | 用户要求订阅方式去掉并发限制、与 API 方式一致；新增 AI-007，先写并发用例在原代码上复现，再删除信号量，同步规格与文档 | 提 PR 发往 `develop` |
| 2026-11-06 / Claude | [#194](https://github.com/zhul-cloud/zhul-trade-erp/pull/194) CI 通过后合并到 `develop`（`8a5fc1c4`） | 随下一版发布后验收 |
| 2026-11-06 / Claude | 随 v1.9.1 发布到生产：v1.9.1 已部署（[#197](https://github.com/zhul-cloud/zhul-trade-erp/pull/197) `e5487fbd`，生产发版运行 42210502306 成功，健康检查通过，2026-11-06）；合回 develop 见 `chore/back-merge-1.9.1` | 生产验收 |
