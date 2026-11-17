# AI-015 Codex 订阅登录失效时仍显示可用、失败被误判为用量上限

## 结论 / 下一步

已修复，待 PR 合并后关闭。服务端拒绝登录（401 / unauthorized）现在归为 auth，连接方式按「未登录」暂停，不再记成「用量上限」；AI 设置里点「测试」时，Codex 除检查本机登录外还会列一次模型向服务端确认（不调用模型、不耗额度），过期时显示「登录已失效」。第二步（用户追问「怎么还是显示可用」）：卡片徽标也向服务端确认，登录过期显示「不可用」；确认结果缓存 5 分钟，重新登录（登录文件变化）后立即重查。本机 Codex 需重新登录才能使用。

## 范围与负责人

| 字段 | 内容 |
| --- | --- |
| 负责人 / 协作者 | Ezio · Claude Code（会话 1a5ed417） |
| 关联 ID / 原专题 | AI-015；openspec ai-connection-failover（已归档） |
| 目标与影响范围 | 订阅连接方式的登录状态显示与执行失败原因分类；涉及 `scripts/ai-orchestrator/subscription.py`、`server.py` 的 `failover_reason`。不涉及 API Key 方式 |
| 起点 commit / 工作分支 | 发现于 `feat/product-content-ai`；修复分支 `fix/ai-015-subscription-auth`（基于 develop，含 #382） |
| OpenSpec | 缺陷修复，不新开提案 |
| PR / 目标分支 | 未创建 |
| 合并证据 | 未合并 |
| 目标版本 / 环境 | 本机发现；生产是否同样过期未查（只读核对需授权） |

## 实施与验证结果

- 2026-11-17 本机试跑 product-content 技能，Codex 订阅 9 秒失败，编排服务日志：`连接方式 subscription:openai 失败（usage_limit）`，回调错误「订阅 CLI 调用失败；请检查登录、订阅额度、模型与容器运行环境」。
- 容器内 `codex exec` 最小请求复现：`workspace routing discovery unauthorized (401)`，重连 5 次后失败；`codex login status` 仍显示已用 ChatGPT 登录，所以卡片为「可用」。
- 同时段 Claude 订阅正常完成同一技能（约 9.5 分钟）。
- 修复：`subscription.py` 新增 `unauthorized()`；worker 兜底异常文本含 unauthorized / 401 时回传 `auth`；`run_codex` 回合失败时按 `codex_error_info`（`unauthorized` 或上游 HTTP 401）归为 `auth`；`codex_models` 遇 `auth` 抛 `SubscriptionCallError(code="auth")`。`server.py` 的 `test_connection` 对已登录的 Codex 再调 `codex_models` 确认。
- 自动化：`test_subscription.py`（401 文本、worker auth、回合失败四种错误码）、`test_diagnostics.py`（测试连接三种结果）；编排服务全部 177 个测试在 2 GB 限内存容器内通过。
- 本机实测：用容器里真实的过期登录调用 `codex_models`，返回 `SubscriptionCallError auth`（改前为「获取 Codex 模型列表失败」）。
- 第一步 PR #383 已合并 develop。
- 第二步：`subscription.codex_confirmed` 列模型确认并缓存（只缓存确定结果）；编排服务 `/status` 由 `subscription_status` 并行检查两家，Codex 已登录时再确认，整次不超过 20 秒（后端超时 30 秒）；「测试」每次重新确认。编排服务 186 个测试在 2 GB 容器通过；本机重建编排服务后，`/status` 返回 Codex 未登录（约 8 秒），BrowserOS neo 实测 AI 设置卡片显示「不可用」，点测试提示「登录已失效（服务端拒绝）」。
