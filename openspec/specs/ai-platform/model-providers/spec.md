# ai-platform/model-providers Specification

## Purpose
约定 AI 编排服务调用哪家模型服务商、每类任务用哪个模型和多大思考强度，以及这些配置的取值、默认值与配置错误时的表现，使部署方只改配置就能切换服务商和模型。

## Requirements

### Requirement: 按配置选择模型服务商
AI 编排服务 SHALL 按配置 `AI_ORCHESTRATOR_PROVIDER` 选择调用的模型服务商，可选 `claude`、`openai`，留空时为 `claude`。服务商适用于真实 API 模式（`AI_ORCHESTRATOR_MODE=api`）和部署者个人订阅模式（`subscription`）；假数据模式（`stub`）不调用任何服务商，行为不变。同一个编排服务实例的所有任务使用同一家服务商。

#### Scenario: 未配置服务商时使用 Claude
- **WHEN** `AI_ORCHESTRATOR_MODE=api`，`AI_ORCHESTRATOR_PROVIDER` 留空，后端提交一个询盘解析任务
- **THEN** 编排服务调用 Claude 完成解析，并按原有契约回调结果

#### Scenario: 切换到 OpenAI
- **WHEN** API 模式中，部署方把 `AI_ORCHESTRATOR_PROVIDER` 改为 `openai`、把 `AI_ORCHESTRATOR_API_KEY` 换成 OpenAI 的 Key 并重启编排服务，后端提交一个询盘解析任务
- **THEN** 编排服务调用 OpenAI 完成解析，回调结果的结构与使用 Claude 时一致，后端无需任何改动

#### Scenario: 假数据模式不受服务商影响
- **WHEN** `AI_ORCHESTRATOR_MODE=stub`，`AI_ORCHESTRATOR_PROVIDER=openai`，且未填写任何 Key
- **THEN** 编排服务照常返回固定假数据，不调用任何服务商，也不因缺少 Key 报错

### Requirement: 使用统一的 Key 变量
AI 编排服务在 API 模式 SHALL 从 `AI_ORCHESTRATOR_API_KEY` 读取当前服务商的 Key，不再读取 `ANTHROPIC_API_KEY`；部署方需填写与 `AI_ORCHESTRATOR_PROVIDER` 对应的那家服务商的 Key。Key 为空时，服务仍正常启动并打印警告；此时每个任务都回调失败，失败原因写明缺少 `AI_ORCHESTRATOR_API_KEY`。订阅模式不使用该 Key，由官方 CLI 管理个人登录，且不能自动回退 API。

#### Scenario: 缺少 Key
- **WHEN** API 模式中 `AI_ORCHESTRATOR_API_KEY` 为空，后端提交一个翻译任务
- **THEN** 编排服务回调失败，失败原因包含 `AI_ORCHESTRATOR_API_KEY`

#### Scenario: Key 与服务商不匹配
- **WHEN** API 模式中 `AI_ORCHESTRATOR_PROVIDER=openai`，`AI_ORCHESTRATOR_API_KEY` 填的是 Claude 的 Key
- **THEN** 任务回调失败，失败原因带有服务商返回的认证错误，不会改用其他服务商

#### Scenario: 旧变量名不再生效
- **WHEN** API 模式中部署环境只设置了 `ANTHROPIC_API_KEY`，未设置 `AI_ORCHESTRATOR_API_KEY`
- **THEN** 编排服务视为未配置 Key，启动警告与任务失败原因都指向 `AI_ORCHESTRATOR_API_KEY`

### Requirement: 按任务配置模型并提供推荐默认值
AI 编排服务 SHALL 用 `AI_ORCHESTRATOR_PARSE_MODEL` 指定询盘解析的模型，用 `AI_ORCHESTRATOR_TRANSLATE_MODEL` 指定型号描述翻译与联系人名音译的模型，不再读取 `AI_ORCHESTRATOR_MODEL`。API 模式留空时使用当前服务商的推荐默认值：Claude 为解析 `claude-opus-5-5`、翻译 `claude-haiku-5-5`；OpenAI 为解析 `gpt-6.1-sol`、翻译 `gpt-6-luna`。订阅模式 Claude 留空默认解析 `opus`、翻译 `haiku`，Codex 留空使用账户默认模型，不沿用 API 模型默认值。填写的模型名原样传给当前执行器，不做校验，不默默替换模型。

#### Scenario: 留空使用推荐模型
- **WHEN** API 模式中 `AI_ORCHESTRATOR_PROVIDER=openai`，两个模型变量都留空
- **THEN** 询盘解析使用 `gpt-6.1-sol`，两类翻译使用 `gpt-6-luna`，启动日志列出实际使用的服务商与两个模型

#### Scenario: 指定模型
- **WHEN** API 模式中 `AI_ORCHESTRATOR_PROVIDER=claude`，`AI_ORCHESTRATOR_PARSE_MODEL=claude-sonnet-5-5`
- **THEN** 询盘解析使用 `claude-sonnet-5-5`，翻译仍使用推荐的 `claude-haiku-5-5`

#### Scenario: 模型名与服务商不匹配
- **WHEN** API 模式中 `AI_ORCHESTRATOR_PROVIDER=openai`，`AI_ORCHESTRATOR_PARSE_MODEL=claude-opus-5-5`
- **THEN** 解析任务回调失败，失败原因带有服务商返回的错误信息

#### Scenario: 订阅模型默认值
- **WHEN** subscription 模式中模型变量留空
- **THEN** Claude 使用 opus/haiku，Codex 不指定模型让账户选择默认，不沿用 API 模型默认值；启动日志列出服务商及所用模型或“账户默认”

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

### Requirement: 部署者个人订阅模式
编排服务 SHALL 支持 `AI_ORCHESTRATOR_MODE=subscription`，由 `AI_ORCHESTRATOR_PROVIDER=openai|claude` 选择官方 Codex Python SDK 或原版 Claude Code CLI。订阅模式不得使用 API Key 或自动回退 API；部署者本人通过容器内官方 CLI 登录，项目不代收 token，不提供账号共享池。

#### Scenario: Codex 订阅无 API Key
- **WHEN** 模式为 subscription、服务商为 openai，专用目录已由部署者完成 ChatGPT 登录，API Key 为空
- **THEN** 编排服务通过官方 Codex SDK 执行任务，并使用原回调契约

#### Scenario: 缺少个人订阅登录
- **WHEN** 未登录或仅配置 API Key，订阅模式接收任务
- **THEN** 任务失败回调明确提示在容器内通过官方 CLI 登录，不转用 API

#### Scenario: 默认与持久登录目录
- **WHEN** Docker 按默认 Compose 运行订阅服务
- **THEN** 认证保存在专用临时 HOME，容器重建后需重新登录；部署者可按注释选择独占持久目录，不复制宿主个人目录

### Requirement: 订阅任务隔离与完整结果
订阅任务 SHALL 单实例串行执行，使用独立临时工作目录与 Linux 进程组，禁用 shell/编辑、外部 MCP/hooks/插件及交互审批；图片/PDF 必须实际处理，超限明确失败；只有完整且符合 JSON Schema 的结果可以成功回调。

#### Scenario: 超时或子进程异常
- **WHEN** 订阅调用超时或 CLI 返回不完整结果
- **THEN** 服务终止本任务的整个进程组并失败回调，不留下继续占用额度的子进程

#### Scenario: 搜索阈值
- **WHEN** 观察到第 N 个不同搜索/网页操作事件，N 为 AI_ORCHESTRATOR_MAX_WEB_SEARCHES
- **THEN** 任务被保守终止并失败回调，操作开始时可能已有在途请求，不宣称硬计费上限

#### Scenario: 真实图片与扫描 PDF
- **WHEN** 任务含受支持图片或不超过20页的未加密 PDF，附件总大小不超过25MiB且图片/PDF页合计不超过20
- **THEN** 图片转换及 PDF 逐页渲染后输入模型；不支持、损坏或超限则明确失败，不静默丢失
