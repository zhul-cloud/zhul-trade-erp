# AI-015 Codex 订阅登录失效时仍显示可用、失败被误判为用量上限

## 结论 / 下一步

已定位，未修复。本机 Codex 订阅的登录文件还在，但服务端已拒绝（401）：AI 设置卡片按 `codex login status` 显示「可用」；执行时 `run_codex` 把所有未完成的回合都归为 runtime，编排服务据此记为「用量上限」并暂停，原因不对。下一步：`run_codex` 识别回合失败中的 401 / unauthorized 归为 auth（「未登录」）；卡片状态是否需要真实调用核验另评估（会消耗订阅额度）。修复单独发 PR，不混入 product-content-ai。

## 范围与负责人

| 字段 | 内容 |
| --- | --- |
| 负责人 / 协作者 | Ezio · Claude Code（会话 1a5ed417） |
| 关联 ID / 原专题 | AI-015；openspec ai-connection-failover（已归档） |
| 目标与影响范围 | 订阅连接方式的登录状态显示与执行失败原因分类；涉及 `scripts/ai-orchestrator/subscription.py`、`server.py` 的 `failover_reason`。不涉及 API Key 方式 |
| 起点 commit / 工作分支 | 发现于 `feat/product-content-ai`（基于 develop ef4c9e88 之后）；修复分支未创建 |
| OpenSpec | 缺陷修复，不新开提案 |
| PR / 目标分支 | 未创建 |
| 合并证据 | 未合并 |
| 目标版本 / 环境 | 本机发现；生产是否同样过期未查（只读核对需授权） |

## 实施与验证结果

- 2026-11-17 本机试跑 product-content 技能，Codex 订阅 9 秒失败，编排服务日志：`连接方式 subscription:openai 失败（usage_limit）`，回调错误「订阅 CLI 调用失败；请检查登录、订阅额度、模型与容器运行环境」。
- 容器内 `codex exec` 最小请求复现：`workspace routing discovery unauthorized (401)`，重连 5 次后失败；`codex login status` 仍显示已用 ChatGPT 登录，所以卡片为「可用」。
- 同时段 Claude 订阅正常完成同一技能（约 9.5 分钟）。
