## MODIFIED Requirements

### Requirement: 按配置选择模型服务商
AI 编排服务 SHALL 按配置 `AI_ORCHESTRATOR_PROVIDER` 选择 `claude` 或 `openai`，留空为 `claude`。服务商适用于真实 API 模式和部署者个人订阅模式；stub 不调用任何服务商。同一编排实例的全部任务使用同一家服务商。

#### Scenario: 未配置服务商时使用 Claude
- **WHEN** 模式为 api、服务商留空
- **THEN** 服务仍通过 Anthropic API 处理任务，默认模型与原有回调保持兼容

#### Scenario: 切换到 OpenAI
- **WHEN** api 模式改为 openai、填对应Key并重启后收到解析任务
- **THEN** 服务使用OpenAI处理并按既有结果结构回调，后端无需改动

#### Scenario: 假数据模式不受服务商影响
- **WHEN** 模式为 stub、没有 Key 或 CLI 登录
- **THEN** 占位脚本继续返回固定假数据，不调用服务商

### Requirement: 使用统一的 Key 变量
API 模式 SHALL 仅从 `AI_ORCHESTRATOR_API_KEY` 读取当前服务商的 Key，不读取旧 API Key 环境变量；Key 为空时服务可启动且警告，每个任务失败回调指明缺少配置。订阅模式不使用该 Key，而由官方 CLI 管理个人登录，且不能回退 API。

#### Scenario: 缺少 Key
- **WHEN** api 模式中 AI_ORCHESTRATOR_API_KEY 为空，即使设置旧 ANTHROPIC_API_KEY
- **THEN** 任务失败原因包含 AI_ORCHESTRATOR_API_KEY

#### Scenario: 旧变量名不再生效
- **WHEN** api 模式只设置旧 ANTHROPIC_API_KEY，没有 AI_ORCHESTRATOR_API_KEY
- **THEN** 视为未配置Key，启动警告与任务失败均指向 AI_ORCHESTRATOR_API_KEY

#### Scenario: Key 与服务商不匹配
- **WHEN** api 模式的 Key 属于另一家服务商
- **THEN** 任务认证失败且不改用其他服务商

### Requirement: 按任务配置模型并提供推荐默认值
编排服务 SHALL 通过 `AI_ORCHESTRATOR_PARSE_MODEL` 和 `AI_ORCHESTRATOR_TRANSLATE_MODEL` 配置两类任务模型。API 的原有模型默认值保持不变；订阅 Claude 留空默认解析 opus、翻译 haiku，订阅 Codex 留空使用账户默认模型。显式配置原样传给当前执行器，不默默降级。

#### Scenario: 订阅模型默认值
- **WHEN** subscription 模式中模型变量留空
- **THEN** Claude 使用 opus/haiku，Codex 不指定模型让账户选择默认，不沿用API模型默认值

#### Scenario: 留空使用推荐模型
- **WHEN** api 模式中模型变量留空
- **THEN** Claude 使用 claude-opus-5-5/claude-haiku-5-5，OpenAI 使用 gpt-6.1-sol/gpt-6-luna

#### Scenario: 指定模型
- **WHEN** api 模式为claude、AI_ORCHESTRATOR_PARSE_MODEL=claude-sonnet-5-5
- **THEN** 解析使用该模型，翻译仍使用默认claude-haiku-5-5

#### Scenario: 模型名与服务商不匹配
- **WHEN** api 模式为openai却配置claude-opus-5-5
- **THEN** 任务失败原因带有服务商错误，不悄悄替换模型

## ADDED Requirements

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
订阅任务 SHALL 单实例串行执行，使用独立临时工作目录与 Linux 进程组，禁用 shell/编辑、外部 MCP/hooks/插件及交互审批；图片/PDF必须实际处理，超限明确失败；只有完整且符合 JSON Schema 的结果可以成功回调。

#### Scenario: 超时或子进程异常
- **WHEN** 订阅调用超时或 CLI 返回不完整结果
- **THEN** 服务终止本任务的整个进程组并失败回调，不留下继续占用额度的子进程

#### Scenario: 搜索阈值
- **WHEN** 观察到第 N 个不同搜索/网页操作事件，N 为 AI_ORCHESTRATOR_MAX_WEB_SEARCHES
- **THEN** 任务被保守终止并失败回调，操作开始时可能已有在途请求，不宣称硬计费上限

#### Scenario: 真实图片与扫描 PDF
- **WHEN** 任务含受支持图片或不超过20页的未加密PDF，附件总大小不超过25MiB且图片/PDF页合计不超过20
- **THEN** 图片转换及PDF逐页渲染后输入模型；不支持、损坏或超限则明确失败，不静默丢失
