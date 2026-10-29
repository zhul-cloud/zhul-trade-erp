# Design

## Context

见 proposal.md。生产依赖 Anthropic 1.14.0，`BetaMessageStream.request_id` 公开属性读取 HTTP 响应头，SSE 合成的 `ParsedBetaMessage` 不保证 `_request_id`。旧 FakeClaude 总给最终消息附加该属性，遗漏了真实 SDK 类型差异。

## Goals / Non-Goals

**Goals:** 最小修复观测元数据读取；真实 SDK 离线 SSE 测试完整解析/翻译/成功回调；核对所有现有询盘输入格式链路。

**Non-Goals:** 不改变业务 Schema、附件处理能力、模型、提示词、搜索、订阅、配置项、数据库或生产运行状态。

## Decisions

1. 在 stream 上下文中读取公开 `stream.request_id` 保存为本地值供最终日志使用；header 缺失时值为 None 安全记录。不访问最终业务消息私有 `_request_id`，不吞业务异常。
2. 使用镜像已有的真实 Anthropic 1.14.0 和 `httpx2.MockTransport` 产生完整 SSE 事件，拦截 HTTP 请求而不模拟 SDK 消息类；直接证明消息没有 `_request_id`，通过完整 `call_parse`、`call_translate`、`process_job` 红绿验证。旧 FakeClaude 改为 stream 提供 request_id。
3. 有效 PNG/PDF 以内存合成，检查发出消息 type/media_type/base64 与文本相同；Excel/XLSX/CSV 继续由后端 `SpreadsheetText` 提取后拼入 rawContent，补独立纯单测，不修改生产链路，不跑依赖数据库的后端集成。

## Risks / Trade-offs

- 本地 SSE 验证协议与响应处理，不能代表云模型已实测所有格式 → 明确区分离线协议、Java 提取单测与后续经授权的单次合成 API 实测。
- 日志修复位于公共 API 方法，会影响解析与翻译 → 两者均用真实 SDK 覆盖，保留既有错误/续传/订阅回归。
- 表格和附件可能走不同入口 → 核对后端提取/临时链接及 Python 消息构造路径，保留有效样本的回归证据。

## Migration Plan

无环境项、依赖或数据库迁移。先面向最新 release 建修复 PR；生产发布需要后续明确授权，按现有流程执行。
