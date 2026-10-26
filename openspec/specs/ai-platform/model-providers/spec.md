# ai-platform/model-providers Specification

## Purpose
约定 AI 编排服务调用哪家模型服务商、每类任务用哪个模型和多大思考强度，以及这些配置的取值、默认值与配置错误时的表现，使部署方只改配置就能切换服务商和模型。

## Requirements

### Requirement: 按配置选择模型服务商
AI 编排服务 SHALL 按配置 `AI_ORCHESTRATOR_PROVIDER` 选择调用的模型服务商，可选 `claude`、`openai`，留空时为 `claude`。服务商只对真实调用模式（`AI_ORCHESTRATOR_MODE=api`）生效；假数据模式（`stub`）不调用任何服务商，行为不变。同一个编排服务实例的所有任务使用同一家服务商。

#### Scenario: 未配置服务商时使用 Claude
- **WHEN** `AI_ORCHESTRATOR_MODE=api`，`AI_ORCHESTRATOR_PROVIDER` 留空，后端提交一个询盘解析任务
- **THEN** 编排服务调用 Claude 完成解析，并按原有契约回调结果

#### Scenario: 切换到 OpenAI
- **WHEN** 部署方把 `AI_ORCHESTRATOR_PROVIDER` 改为 `openai`、把 `AI_ORCHESTRATOR_API_KEY` 换成 OpenAI 的 Key 并重启编排服务，后端提交一个询盘解析任务
- **THEN** 编排服务调用 OpenAI 完成解析，回调结果的结构与使用 Claude 时一致，后端无需任何改动

#### Scenario: 假数据模式不受服务商影响
- **WHEN** `AI_ORCHESTRATOR_MODE=stub`，`AI_ORCHESTRATOR_PROVIDER=openai`，且未填写任何 Key
- **THEN** 编排服务照常返回固定假数据，不调用任何服务商，也不因缺少 Key 报错

### Requirement: 使用统一的 Key 变量
AI 编排服务 SHALL 从 `AI_ORCHESTRATOR_API_KEY` 读取当前服务商的 Key，不再读取 `ANTHROPIC_API_KEY`；部署方需填写与 `AI_ORCHESTRATOR_PROVIDER` 对应的那家服务商的 Key。Key 为空时，服务仍正常启动并打印警告；此时每个任务都回调失败，失败原因写明缺少 `AI_ORCHESTRATOR_API_KEY`。

#### Scenario: 缺少 Key
- **WHEN** `AI_ORCHESTRATOR_API_KEY` 为空，后端提交一个翻译任务
- **THEN** 编排服务回调失败，失败原因包含 `AI_ORCHESTRATOR_API_KEY`

#### Scenario: Key 与服务商不匹配
- **WHEN** `AI_ORCHESTRATOR_PROVIDER=openai`，`AI_ORCHESTRATOR_API_KEY` 填的是 Claude 的 Key
- **THEN** 任务回调失败，失败原因带有服务商返回的认证错误，不会改用其他服务商

#### Scenario: 旧变量名不再生效
- **WHEN** 部署环境只设置了 `ANTHROPIC_API_KEY`，未设置 `AI_ORCHESTRATOR_API_KEY`
- **THEN** 编排服务视为未配置 Key，启动警告与任务失败原因都指向 `AI_ORCHESTRATOR_API_KEY`

### Requirement: 按任务配置模型并提供推荐默认值
AI 编排服务 SHALL 用 `AI_ORCHESTRATOR_PARSE_MODEL` 指定询盘解析的模型，用 `AI_ORCHESTRATOR_TRANSLATE_MODEL` 指定型号描述翻译与联系人名音译的模型，不再读取 `AI_ORCHESTRATOR_MODEL`。留空时使用当前服务商的推荐默认值：Claude 为解析 `claude-opus-5-5`、翻译 `claude-haiku-5-5`；OpenAI 为解析 `gpt-6.1-sol`、翻译 `gpt-6-luna`。填写的模型名原样传给服务商，不做校验。

#### Scenario: 留空使用推荐模型
- **WHEN** `AI_ORCHESTRATOR_PROVIDER=openai`，两个模型变量都留空
- **THEN** 询盘解析使用 `gpt-6.1-sol`，两类翻译使用 `gpt-6-luna`，启动日志列出实际使用的服务商与两个模型

#### Scenario: 指定模型
- **WHEN** `AI_ORCHESTRATOR_PROVIDER=claude`，`AI_ORCHESTRATOR_PARSE_MODEL=claude-sonnet-5-5`
- **THEN** 询盘解析使用 `claude-sonnet-5-5`，翻译仍使用推荐的 `claude-haiku-5-5`

#### Scenario: 模型名与服务商不匹配
- **WHEN** `AI_ORCHESTRATOR_PROVIDER=openai`，`AI_ORCHESTRATOR_PARSE_MODEL=claude-opus-5-5`
- **THEN** 解析任务回调失败，失败原因带有服务商返回的错误信息

### Requirement: 按任务配置思考强度
AI 编排服务 SHALL 用 `AI_ORCHESTRATOR_PARSE_EFFORT` 和 `AI_ORCHESTRATOR_TRANSLATE_EFFORT` 分别设置询盘解析与翻译的思考强度，可选 `low`、`medium`、`high`、`xhigh`、`max`，留空均为 `medium`，取值原样传给当前服务商。取值不在可选范围内时，服务启动即报错退出，并说明变量名与可选值。

#### Scenario: 默认中等强度
- **WHEN** 两个思考强度变量都留空
- **THEN** 询盘解析与翻译都以 `medium` 强度调用服务商

#### Scenario: 解析使用更高强度
- **WHEN** `AI_ORCHESTRATOR_PARSE_EFFORT=high`
- **THEN** 询盘解析以 `high` 强度调用，翻译仍为 `medium`

#### Scenario: 非法取值
- **WHEN** `AI_ORCHESTRATOR_TRANSLATE_EFFORT=fast`
- **THEN** 编排服务启动失败退出，错误信息包含 `AI_ORCHESTRATOR_TRANSLATE_EFFORT` 和可选值列表

### Requirement: 非法服务商取值启动即报错
AI 编排服务 SHALL 在 `AI_ORCHESTRATOR_PROVIDER` 不是 `claude`、`openai` 之一时启动即报错退出，错误信息列出可选值，不回退到其他服务商。

#### Scenario: 不支持的服务商
- **WHEN** `AI_ORCHESTRATOR_PROVIDER=deepseek`
- **THEN** 编排服务启动失败退出，错误信息说明只支持 `claude`、`openai`

### Requirement: 各服务商完成相同的解析能力
无论使用哪家服务商，询盘解析 SHALL 支持图片与 PDF 附件、对每个提取到的型号联网搜索验证、按后端约定的 JSON 结构返回结果，并受单次解析的联网搜索次数上限 `AI_ORCHESTRATOR_MAX_WEB_SEARCHES` 约束；翻译任务不联网搜索。服务商返回的结果缺少约定的必填字段或无法解析时，任务回调失败。

#### Scenario: OpenAI 解析带 PDF 的询盘
- **WHEN** 服务商为 `openai`，后端提交一条带 PDF 型号清单的询盘解析任务
- **THEN** 编排服务把 PDF 交给模型识别并逐个型号联网搜索，回调的结果包含与 Claude 相同的分组、型号、置信度等字段

#### Scenario: 可选字段未给出
- **WHEN** OpenAI 返回的某个型号没有给出替代型号等可选字段
- **THEN** 回调结果中该型号不包含这些字段，与 Claude 未给出时的表现一致，后端照常处理
