# Proposal

## Why

Claude API Key 模式在询盘解析时返回 `Schema is too complex`，导致任务失败。现有业务 Schema 有 13 个可选字段，未超过官方 24 个显式上限，但嵌套可选项仍可能触发内部编译复杂度限制，需要保留业务契约并验证简化后的服务商 Schema。

## What Changes

- 仅 Claude API 出站 Schema 使用全部字段必填、原可选字段可空的副本，减少可选项组合。
- Claude API 返回后移除 null 字段并按原业务 Schema 校验；保留空串、0、空数组，非法结果失败回调。
- 保留所有业务字段和原 Schema；订阅模式及 OpenAI API 行为保持兼容。
- 增加离线回归并使用受控合成输入验证真实 API，不能以本地阈值检查替代服务端编译验证。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `ai-platform/model-providers`: 明确 Claude API Schema 适配、可选字段归一化、必填字段校验与订阅隔离。

## Impact

仅影响 `scripts/ai-orchestrator/server.py`、`test_server.py` 与本 OpenSpec 变更。复用现有 `jsonschema` 依赖；不新增配置、数据库迁移、金额计算、跨模块事务或 UI。回调契约与业务字段不变，不部署生产。
