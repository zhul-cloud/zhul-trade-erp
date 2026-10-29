# Proposal

## Why

Claude API 流式调用已得到有效结构化结果后，日志访问 `ParsedBetaMessage._request_id` 会抛出 AttributeError，误将任务回调为失败。上轮验证覆盖了真实 Schema 候选请求，未经过完整服务响应处理路径，需要用真实 SDK 的 SSE 回归弥补这一缺口。

## What Changes

- 从 SDK 支持的流式 HTTP 响应取得可选 request-id，缺少诊断元数据不影响有效业务结果。
- 使用真实 Anthropic SDK 和离线 SSE 传输验证完整解析与回调路径，先复现失败再验证修复。
- 核对文本、Excel/CSV 转文本、图片和 PDF 输入链路；通过合成有效图片/PDF 验证 API 请求协议形态。
- 保持 Schema、业务字段、拒答/超时/长度上限/续传与订阅行为。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `ai-platform/model-providers`: 明确可选诊断元数据不改变有效结构化结果的成功状态。

## Impact

预计只改 `scripts/ai-orchestrator/server.py` 与自动回归测试，以及本变更规划。无需新增依赖、环境项、数据库迁移、UI 或生产数据操作；另补后端 SpreadsheetText 独立单测，仅验证既有提取路径，不修改后端生产代码。
