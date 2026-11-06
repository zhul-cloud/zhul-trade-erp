# AI-008 编排服务拒绝请求前读完请求体

## 范围与负责人

| 字段 | 内容 |
| --- | --- |
| 负责人 / 协作者 | Claude（实施与验证）；用户 Ezio 验收 |
| 关联 ID / 原专题 | [AI-008](../../04-ai-and-operations/01-AI任务可靠性与诊断.md)；验证 [AI-007](2026-11-06-AI-007-subscription-concurrency.md) 时发现 |
| 目标与影响范围 | AI 编排服务 HTTP 入口在返回 401 / 404 前读完请求体。不改鉴权规则，不改后端、前端、数据库、配置 |
| 起点 commit / 工作分支 | develop `c0a23219`；分支 `fix/ai-008-token-reject-drain`，合入了尚未合并的 AI-007 分支以免台账冲突 |
| OpenSpec | 不适用：缺陷修复，接口行为不变 |
| PR / 目标分支 | [#195](https://github.com/zhul-cloud/zhul-trade-erp/pull/195)，发往 `develop`，CI 通过后自动合并 |
| 合并证据 | `develop`：#195 `120c8acb` |
| 目标版本 / 环境 | 随下一版发布；需要重建 AI 编排服务镜像 |

## 实施与验证结果

- **根因：** [`server.py`](../../../scripts/ai-orchestrator/server.py) 拒绝请求时没读请求体就回应并关闭连接；Windows 对带未读数据的 socket 发 RST，客户端读响应时报 WinError 10053。
- **修改：** 新增 `Handler._reject`，令牌错误的 401 和 `do_POST` 未知路径的 404 都改用它。
  - 请求体不超过 `MAX_REJECT_DRAIN_BYTES`（1 MiB）时先读完再回应；
  - 更大或 `Content-Length` 非法时不读，明确回应 `Connection: close`，避免未授权请求占用服务。

| 命令 / 操作与证据 | 环境、commit / 版本 | 预期 | 实际 | 结论 |
| --- | --- | --- | --- | --- |
| [`test_server.py`](../../../scripts/ai-orchestrator/test_server.py) `RejectDrainTest`：直接调用 Handler，检查回应状态码与请求体读取位置 | 本机 Windows，Python 3.13 venv | 401 / 404 前读完请求体；超大请求体不读、关闭连接 | 修复前 3 个全部失败（读了 0 / 4209 字节、0 / 8 字节，没有关闭连接）；修复后通过 | 通过（缺陷已复现并修复） |
| `python -m unittest test_server.HttpTokenTest` 连续 15 次 | 同上 | 全部通过 | 修复前 develop 原代码 8 次中失败 3 次；修复后 15/15 通过 | 通过 |
| 编排服务全部 Python 测试，连续 5 次 | 同上 | 全部通过 | 每次 100 个用例通过，跳过 3 个 Linux 专用用例 | 通过 |

- **未覆盖：** Linux 容器里的真实 HTTP 行为没有单独验证，由 CI 跑同一套测试。

## 合并、部署与逐 ID 验收

| ID | 修复 / 验收范围 | 部署与目标环境验收证据 | 验收人 / 日期 | 结论与剩余项 |
| --- | --- | --- | --- | --- |
| AI-008 | 令牌错误、路径不存在时客户端稳定收到 401 / 404 | 未部署；测试环境验收即可，生产不涉及业务功能 | — | 已合并待发布：随下一版发布后验收 |

## 阻塞与下一步

- 等 AI-007（#194）合并后，本 PR 的改动只剩 AI-008；合并后随下一版发布。

## 事件记录

| 日期 / 作者 | 事件与证据 | 下一步 / 交接 |
| --- | --- | --- |
| 2026-11-06 / Claude | 验证 AI-007 时发现 `HttpTokenTest` 在 Windows 偶发失败，在 develop 原代码上确认（8 次中 3 次）；新增 AI-008，先写 `RejectDrainTest` 复现，再修复 | 提 PR 发往 `develop` |
| 2026-11-06 / Claude | [#195](https://github.com/zhul-cloud/zhul-trade-erp/pull/195) CI 通过后合并到 `develop`（`120c8acb`） | 随下一版发布后验收 |
