# Proposal

## Why

AI 编排服务的服务商、API Key、模型、思考强度、搜索上限现在都写在 `.env`，整个部署只有一份：所有租户共用同一个 Key，AI 费用都记在部署者账号上；改任何一项都要改服务器文件并重建容器。系统要面向多个租户，每个租户应当用自己的 Key、自己承担费用、自己调整模型与搜索上限，并在系统里随时修改。加密已在 `crypto-aes-gcm` 中换成 AES-GCM，可以安全地存放各租户的 Key。

## What Changes

- 每个租户在「业务设置 → AI 设置」中维护自己的 AI 配置：运行方式、服务商、API Key、解析模型、翻译模型、解析与翻译的思考强度、单次解析的最大搜索次数。修改后下一个任务立即生效，不需重启。
- 配置存独立表 `sys_ai_config`（每个租户一行），**不继承平台配置**：租户未配置时，用户主动发起的 AI 操作（询盘解析、型号描述翻译）直接提示「AI 未配置」，不创建任务；保存商机时顺带触发的联系人名音译跳过并记录日志，不影响保存。
- API Key 用 `system/data-encryption` 的方式加密存储；接口只返回脱敏值（如 `sk-ant-••••••••••••••••abcd`），留空保存表示不修改，修改时界面二次确认，日志中不出现明文。
- 运行方式「订阅」只有平台租户（`tenant_id = 0`）能选，其他租户只能用 API 方式；后端校验，不只靠界面隐藏。
- 解析模型与翻译模型可从下拉列表选择，也可手动输入：API 方式按 Key 读取服务商的模型列表（OpenAI 过滤非对话模型），Codex 订阅读取账号可用模型，Claude 订阅与无 Key 时只给默认模型；获取列表不产生调用费用。
- 「测试连接」：用页面当前填写的值（Key 留空时用已保存的 Key）检查 Key 是否有效、两个模型是否可用；API 方式调用服务商的模型查询接口，不产生调用费用；订阅方式检查对应 CLI 的登录状态。
- 平台租户的 AI 页显示 Codex 与 Claude 两个 CLI 的订阅登录状态及登录命令。
- **BREAKING** 编排服务不再从 `.env` 读取 `AI_ORCHESTRATOR_PROVIDER`、`AI_ORCHESTRATOR_API_KEY`、`AI_ORCHESTRATOR_PARSE_MODEL`、`AI_ORCHESTRATOR_TRANSLATE_MODEL`、`AI_ORCHESTRATOR_PARSE_EFFORT`、`AI_ORCHESTRATOR_TRANSLATE_EFFORT`、`AI_ORCHESTRATOR_MAX_WEB_SEARCHES`，改为每个任务由后端在请求体 `ai` 字段中传入；这些项从 `.env.example` 与 Compose 中删除。`AI_ORCHESTRATOR_MODE` 只保留 `api`（真实服务）与 `stub`（假数据）两个取值。生产升级后必须在系统中为平台租户填写 AI 配置，否则 AI 不可用（项目尚未正式使用，不做自动导入）。
- 编排服务所有接口校验 `X-AI-Callback-Token`（现在提交接口未校验）；新增 `POST /test`、`POST /models`、`GET /status`。

## Capabilities

### New Capabilities

- `ai-platform/tenant-ai-settings`：租户 AI 配置的维护（字段、默认值、校验、平台租户专属的订阅方式）、Key 的加密与脱敏、未配置时的行为、测试连接与订阅登录状态。

### Modified Capabilities

- `ai-platform/model-providers`：服务商、Key、模型、思考强度、搜索上限由每个任务携带的租户配置决定，不再来自部署环境变量；非法取值改为任务失败而不是启动失败；订阅方式只用于平台租户的任务。
- `ai-platform/task-orchestration`：提交任务时附带当前租户的 AI 配置；当前租户未配置时不创建任务；编排服务校验共享令牌。

## Impact

- 数据库：迁移 `V1.3.17` 新建 `sys_ai_config`（独立表，不复用系统参数表）；`V1.3.18` 新增菜单与按钮权限；均可回退。
- 后端：新增 AI 配置模块（实体、Mapper、Service、Controller：查询、保存、模型列表、测试连接、订阅状态）；`AiTaskService.createAndSubmit` 读取当前租户配置、解密 Key 并随提交传给 `AiOrchestratorClient`；`ContactNameTranslations` 未配置时跳过。
- 编排服务：`server.py` 改为按任务配置调用、所有接口校验令牌、新增 `/test`、`/models` 与 `/status`；`subscription.py` 读取任务传入的服务商；`docker-entrypoint.sh` 只区分 `api` / `stub`；stub 服务忽略 `ai` 字段。
- 前端：新增独立页面「业务设置 → AI 设置」（状态卡、连接与模型两张卡片、模型下拉选择、测试连接、订阅登录状态、底部保存条），按 OpenPencil 原型实现；系统参数页不显示 AI 配置。
- 配置：`.env.example`、`docker-compose.yaml` 删除 7 个 AI 业务配置项（生产 `.env` 中残留不影响，部署检查只校验缺失项，不需提前补配置）；部署文档 `04-AI编排服务.md` 改写。
- 权限：新增独立菜单「业务设置 → AI 设置」与按钮权限「编辑 AI 设置」（`system:ai-config:edit`），迁移 `V1.3.18` 加进所有已有套餐与内置管理员角色；可回退（删除这两条资源）。
