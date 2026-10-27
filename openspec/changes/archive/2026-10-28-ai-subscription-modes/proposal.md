## Why

AI 编排目前仅有 API 与 stub，无法在项目 Docker 部署中使用部署者本人的 Codex/Claude 个人订阅。需要可复现的官方 SDK/CLI 运行与登录流程，同时保留既有 API 和回调契约。

## What Changes

- 新增 `AI_ORCHESTRATOR_MODE=subscription`，保留 `api|stub` 及 `PROVIDER=claude|openai`。
- 官方 Codex Python SDK / 原版 Claude Code CLI 在专用认证目录、隔离任务进程中执行，支持图片/PDF、结构化校验、并发及超时限制。
- Docker 镜像内置固定版本工具；默认登录状态临时保存，文档提供可选持久化。禁止代收凭据与个人订阅共享池。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `ai-platform/model-providers`: 模式与认证、部署运行边界。

## Impact

仅 AI 编排 Python 服务、镜像、Compose、启动辅助、配置示例与部署文档。无后端/前端、数据库迁移、业务事务变更。保留既有后端任务提交和结果回调。
