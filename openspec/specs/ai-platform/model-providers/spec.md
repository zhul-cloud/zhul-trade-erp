# ai-platform/model-providers Specification

## Purpose
约定 AI 编排服务调用哪家模型服务商、每类任务用哪个模型和多大思考强度，以及这些配置的取值、默认值与配置错误时的表现，使部署方只改配置就能切换服务商和模型。

## Requirements

### Requirement: 按配置选择模型服务商
AI 编排服务 SHALL 按每个任务携带的租户 AI 配置选择模型服务商，可选 `claude`、`openai`，留空时为 `claude`。服务商适用于 API 方式和平台租户的订阅方式；部署环境变量 `AI_ORCHESTRATOR_MODE=stub` 时为假数据服务，不调用任何服务商，忽略任务携带的配置。同一个编排服务实例可以同时为不同租户使用不同服务商。

#### Scenario: 未配置服务商时使用 Claude
- **WHEN** 后端提交一个询盘解析任务，携带的配置为 API 方式、服务商留空
- **THEN** 编排服务调用 Claude 完成解析，并按原有契约回调结果

#### Scenario: 切换到 OpenAI
- **WHEN** 租户在系统中把服务商改为 `openai` 并填写 OpenAI 的 Key，随后提交询盘解析
- **THEN** 编排服务调用 OpenAI 完成解析，回调结果的结构与使用 Claude 时一致，编排服务无需重启

#### Scenario: 假数据模式不受服务商影响
- **WHEN** `AI_ORCHESTRATOR_MODE=stub`，任务携带的服务商为 `openai` 且 Key 为空
- **THEN** 编排服务照常返回固定假数据，不调用任何服务商，也不因缺少 Key 报错

### Requirement: 按任务配置模型并提供推荐默认值
AI 编排服务 SHALL 用任务携带的解析模型执行询盘解析，用翻译模型执行型号描述翻译与联系人名音译。模型留空时使用当前服务商的推荐默认值，API 与订阅方式相同：Claude 为解析 `claude-opus-5-5`、翻译 `claude-haiku-5-5`；OpenAI 为解析 `gpt-6.1-sol`、翻译 `gpt-6-luna`。填写的模型名原样传给当前执行器，不做校验，不默默替换模型。

#### Scenario: 留空使用推荐模型
- **WHEN** 任务携带的配置为服务商 `openai`，两个模型都留空
- **THEN** 询盘解析使用 `gpt-6.1-sol`，两类翻译使用 `gpt-6-luna`

#### Scenario: 指定模型
- **WHEN** 任务携带的配置为服务商 `claude`、解析模型 `claude-sonnet-5-5`
- **THEN** 询盘解析使用 `claude-sonnet-5-5`，翻译仍使用推荐的 `claude-haiku-5-5`

#### Scenario: 模型名与服务商不匹配
- **WHEN** 任务携带的配置为服务商 `openai`、解析模型 `claude-opus-5-5`
- **THEN** 解析任务回调失败，失败原因带有服务商返回的错误信息

#### Scenario: 订阅模型默认值
- **WHEN** 平台租户使用订阅方式，模型留空
- **THEN** 与 API 方式相同：Claude 使用 `claude-opus-5-5` / `claude-haiku-5-5`，Codex 使用 `gpt-6.1-sol` / `gpt-6-luna`

### Requirement: 按任务配置思考强度
AI 编排服务 SHALL 按任务携带的解析与翻译思考强度调用服务商，可选 `low`、`medium`、`high`、`xhigh`、`max`，留空均为 `medium`，取值原样传给当前服务商。携带的取值不在可选范围内时，该任务回调失败并说明可选值，不影响其他任务。

#### Scenario: 默认中等强度
- **WHEN** 任务携带的两个思考强度都留空
- **THEN** 询盘解析与翻译都以 `medium` 强度调用服务商

#### Scenario: 解析使用更高强度
- **WHEN** 任务携带的解析思考强度为 `high`
- **THEN** 询盘解析以 `high` 强度调用，翻译仍为 `medium`

#### Scenario: 非法取值
- **WHEN** 任务携带的翻译思考强度为 `fast`
- **THEN** 该任务回调失败，失败原因包含可选值列表；编排服务继续处理其他任务

### Requirement: 各服务商完成相同的解析能力
无论使用哪家服务商，询盘解析 SHALL 支持图片与 PDF 附件、对每个提取到的型号联网搜索验证、按后端约定的 JSON 结构返回结果，并受任务携带的单次解析最大搜索次数约束；翻译任务不联网搜索。服务商返回的结果缺少约定的必填字段或无法解析时，任务回调失败。

#### Scenario: OpenAI 解析带 PDF 的询盘
- **WHEN** 服务商为 `openai`，后端提交一条带 PDF 型号清单的询盘解析任务
- **THEN** 编排服务把 PDF 交给模型识别并逐个型号联网搜索，回调的结果包含与 Claude 相同的分组、型号、置信度等字段

#### Scenario: 可选字段未给出
- **WHEN** OpenAI 返回的某个型号没有给出替代型号等可选字段
- **THEN** 回调结果中该型号不包含这些字段，与 Claude 未给出时的表现一致，后端照常处理

#### Scenario: 租户自定搜索上限
- **WHEN** 租户把最大搜索次数设为 5，提交一条含 10 个型号的询盘解析
- **THEN** 本次解析最多发起 5 次联网搜索

### Requirement: 部署者个人订阅模式
编排服务 SHALL 在任务携带的运行方式为 `subscription` 时，按携带的服务商使用官方 Codex Python SDK（`openai`）或原版 Claude Code CLI（`claude`）执行。订阅方式只用于平台租户的任务，不使用 API Key，不自动回退 API；部署者本人通过容器内官方 CLI 登录，项目不代收 token，不提供账号共享池。

#### Scenario: Codex 订阅无 API Key
- **WHEN** 平台租户的任务携带订阅方式、服务商 `openai`，容器内已由部署者完成 ChatGPT 登录
- **THEN** 编排服务通过官方 Codex SDK 执行任务，并使用原回调契约

#### Scenario: 缺少个人订阅登录
- **WHEN** 任务携带订阅方式但容器内对应 CLI 未登录
- **THEN** 任务失败回调明确提示在容器内通过官方 CLI 登录，不转用 API

#### Scenario: 登录状态持久保存
- **WHEN** Docker 按默认 Compose 运行订阅服务，部署者在容器内完成官方 CLI 登录后停止、重启、重建容器或重新构建镜像
- **THEN** 认证保存在专用 HOME 的 Docker 命名卷中（属主 10001、权限 0700，不在 `storage/` 备份范围内），无需重新登录；删除该卷或执行官方退出命令后才需重新登录；不挂载或复制宿主个人目录

### Requirement: 订阅任务隔离与完整结果
订阅任务 SHALL 单实例串行执行，使用独立临时工作目录与 Linux 进程组，禁用 shell/编辑、外部 MCP/hooks/插件及交互审批；图片/PDF 必须实际处理，超限明确失败；只有完整且符合 JSON Schema 的结果可以成功回调。

#### Scenario: 超时或子进程异常
- **WHEN** 订阅调用超时或 CLI 返回不完整结果
- **THEN** 服务终止本任务的整个进程组并失败回调，不留下继续占用额度的子进程

#### Scenario: 搜索阈值
- **WHEN** 观察到第 N 个不同搜索/网页操作事件，N 为任务携带的最大搜索次数
- **THEN** 任务被保守终止并失败回调，操作开始时可能已有在途请求，不宣称硬计费上限

#### Scenario: 真实图片与扫描 PDF
- **WHEN** 任务含受支持图片或不超过20页的未加密 PDF，附件总大小不超过25MiB且图片/PDF页合计不超过20
- **THEN** 图片转换及 PDF 逐页渲染后输入模型；不支持、损坏或超限则明确失败，不静默丢失
