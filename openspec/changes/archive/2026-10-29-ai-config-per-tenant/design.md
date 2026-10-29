# Design

## Context

- 编排服务 `scripts/ai-orchestrator/server.py` 启动时从环境变量读出服务商、Key、模型、思考强度、搜索上限，存为模块级常量；`subscription.py` 的调用也使用这些常量。提交接口 `POST /skills/{id}/run` 不校验 `X-AI-Callback-Token`（后端发了，编排服务没检查）。
- 后端 `AiTaskServiceImpl.createAndSubmit` 在请求线程里创建任务，事务提交后（`afterCommit`）异步调用 `AiOrchestratorClient.submit(skillId, taskId, inputJson)`；任务输入不落库。调用方三处：询盘解析（用户操作）、型号描述翻译（用户操作）、保存商机时的联系人名音译（`ContactNameTranslations`，副作用）。
- 系统参数 `ConfigServiceImpl` 读取时合并本租户与 `tenant_id = 0` 的配置（租户没有时退回平台值），这与「未配置直接失败、不用平台 Key」相反。项目没有 MyBatis 租户拦截器，各服务按 `TenantContext` 手动加 `tenant_id` 条件。
- 加密使用 `AesUtils`（`system/data-encryption`，AES-GCM）。
- 行为要求见 `specs/ai-platform/tenant-ai-settings`、`model-providers`、`task-orchestration` 三份增量规格。

## Goals / Non-Goals

**Goals:**
- 编排服务变成「按任务配置执行」的无状态执行器，所有业务配置都随任务传入。
- 解密后的 Key 只存在于一次请求的内存中，任何持久化位置与日志都没有明文。
- 改动集中：后端只在 `createAndSubmit` 一处取配置，调用方不改签名。

**Non-Goals:**
- 不做订阅的网页登录（仍在容器内用 CLI 登录）。
- 不做按租户的用量统计与费用限额。
- 不改编排服务的解析与翻译提示词、输出结构和回调契约。
- 不从 `.env` 自动导入现有配置（项目未正式使用，上线后手动配置平台租户）。

## Decisions

### 1. 独立表 `sys_ai_config`，不复用 `sys_config`

每个租户一行（`tenant_id` 唯一索引），字段：`mode`、`provider`、`api_key`（AES-GCM 密文，`varchar(512)`）、`parse_model`、`translate_model`、`parse_effort`、`translate_effort`、`max_web_searches`，加通用字段（`create_time/by`、`update_time/by`）与 `status`（基础配置表，按规范只用 `status`，不加 `deleted_at`）。

- 不复用 `sys_config`：它的读取会退回平台配置，AI 需要「不继承」；还要把 AI 项从通用列表中排除、单独处理加密与校验，改动反而更散。独立表字段有类型，校验和默认值写在一个服务里。
- 没有行即「未配置」；保存时按租户 upsert。

### 2. 在请求线程里取配置快照，随任务提交

`createAndSubmit` 在创建任务之前，用 `TenantContext` 读本租户配置、解密 Key，组装 `AiCallConfig`（含默认值补全后的完整配置），未配置时抛 `AiNotConfiguredException`（继承 `BizException`，提示「AI 未配置，请在业务设置 → AI 设置 中填写」），不创建任务。快照对象传给 `submit` → `AiOrchestratorClient.submit(skillId, taskId, inputJson, aiConfig)`，序列化为请求体字段 `ai`。

- 在请求线程取：`afterCommit` 的异步回调里没有可靠的租户上下文，且「提交那一刻的配置」语义更清楚（规格「任务携带发起时的配置」）。
- 默认值在后端补全后再传：编排服务收到的总是完整配置，日志与回调可以直接说明用了哪个模型；编排服务仍保留同样的默认值表，作为防御（`stub` 及直接调用时）。
- `AiCallConfig` 的 `toString()` 对 Key 脱敏，避免被日志或异常信息带出。
- `ContactNameTranslations.submit` 捕获 `AiNotConfiguredException`，记 `log.info` 后跳过，商机保存不受影响。询盘解析与描述翻译不捕获，异常直接返回给前端。

### 3. 编排服务按任务配置执行

- `server.py` 删除模块级的 `PROVIDER`、`API_KEY`、`PARSE_MODEL` 等常量，新增 `AiConfig`（dataclass，`__repr__` 脱敏 Key）由请求体 `ai` 解析并校验：服务商、思考强度、搜索上限不合法或 API 方式缺 Key 时任务回调失败（不再启动即退出）。
- `call_api`、`_call_claude`、`_call_openai`、`call_subscription` 改为接收 `AiConfig`，每次调用按配置创建 SDK 客户端（创建成本低，避免跨租户复用客户端）。
- 订阅方式只信任后端：后端保证只有平台租户能保存 `subscription`；编排服务不区分租户，照常执行。订阅任务继续全局串行。
- 启动校验只剩部署项：`AI_ORCHESTRATOR_TIMEOUT_SECONDS` 为正整数、`AI_TASK_CALLBACK_TOKEN` 非空（为空时打印警告，沿用现有默认值行为）。

### 4. 所有接口校验共享令牌

`Handler` 在 `do_GET` / `do_POST` 开头比较 `X-AI-Callback-Token` 与 `AI_TASK_CALLBACK_TOKEN`（`hmac.compare_digest`），不一致返回 401。stub 服务同样校验，保持契约一致。后端的提交、测试、状态请求都已带此请求头（测试与状态为新增调用，同样带上）。

### 5. 测试连接与订阅状态

- 编排服务 `POST /test`，请求体为 `ai` 配置：
  - API 方式：用 Key 分别查询解析模型与翻译模型（Anthropic `models.retrieve`、OpenAI `models.retrieve`），返回每项 `ok` 与原因；认证失败直接返回「Key 无效」。只查询模型信息，不产生调用费用。
  - 订阅方式：返回所选服务商 CLI 的登录状态。
- 编排服务 `POST /models`，请求体为 `ai` 配置，返回 `{models: [{id, isDefault}], source}`：
  - API + claude：Anthropic `models.list`（分页取完）；API + openai：OpenAI `models.list`，按名称排除 `embedding`、`tts`、`whisper`、`dall-e`、`image`、`audio`、`realtime`、`transcribe`、`moderation`。
  - 订阅 + openai：Codex SDK `models()`（不含隐藏模型），保留账号默认标记。
  - 订阅 + claude 或无 Key：不调用外部，返回当前服务商的两个默认模型（`source: defaults`）。
  - 与测试连接相同，只读模型信息，不产生调用费用；出错时返回原因（不含 Key）。
- 编排服务 `GET /status`：执行 `codex login status` 与 `claude auth status`（各 10 秒超时，环境同订阅任务），只返回 `{codex: {loggedIn}, claude: {loggedIn}}`，不返回账号信息。
- 后端接口（前缀 `/api/v1/system/ai-config`）：
  - `GET`：本租户配置（Key 只返回 `hasKey` 与脱敏值）。
  - `PUT`：保存；Key 为空串表示不修改；非平台租户提交 `subscription` 返回参数错误。权限 `system:ai-config:edit`。
  - `POST /test`：用请求中的值测试，Key 为空时用已保存的；同步调用编排服务（超时 30 秒）。权限 `system:ai-config:edit`。
  - `POST /models`：与 `/test` 相同的取 Key 规则与权限，返回模型列表；后端不缓存（切换服务商或 Key 后前端重新请求）。
  - `GET /subscription-status`：仅平台租户，需能访问 AI 设置菜单，转发编排服务 `/status`。
- 独立菜单「业务设置 → AI 设置」（`resource` 100118，路径 `/system/ai-settings`）与按钮权限「编辑 AI 设置」（110214，`system:ai-config:edit`）：`GET` 与 `/subscription-status` 要求能访问该菜单，其余接口还要求编辑权限。迁移 `V1.3.18` 新建这两条资源，加进所有已有套餐与内置管理员角色。不与系统参数共用权限：AI 配置是租户自己维护的业务设置，系统参数主要由平台维护。

### 6. 部署配置收敛

- `.env.example`、`docker-compose.yaml` 删除 7 项（`AI_ORCHESTRATOR_PROVIDER`、`API_KEY`、`PARSE_MODEL`、`TRANSLATE_MODEL`、`PARSE_EFFORT`、`TRANSLATE_EFFORT`、`MAX_WEB_SEARCHES`）。生产部署脚本只检查「`.env.example` 有而服务器 `.env` 没有」的键，删除项不会阻断部署；服务器 `.env` 里的残留值不再被读取。
- `AI_ORCHESTRATOR_MODE` 只保留 `api`（真实服务，默认）与 `stub`；`docker-entrypoint.sh` 中非 `stub` 一律启动真实服务，兼容本机 `.env` 里已有的 `subscription` 值（运行方式改由系统配置决定）。
- 本机用 `stub` 联调时，租户仍需在系统里填一份配置（任意 Key）才能创建任务，部署文档写明。

### 7. 前端：独立页面「业务设置 → AI 设置」

页面 `pages/system/ai-settings`，按 OpenPencil 原型 `docs/03-产品原型/00-用户域/07-AI设置/AI设置.op`（S1–S5，用户 2026-10-29 确认）实现，系统参数页不再出现 AI：
- 顶部状态卡：未配置（三步引导）/ 已配置（服务商、脱敏 Key 或个人订阅、最后修改人与时间）/ 测试通过 / 测试失败；「测试连接」逐项显示 API Key、解析模型、翻译模型的结果，未测到的项显示「未检测」。
- 左「服务商与账号」卡：服务商（卡片式单选）、运行方式（仅平台租户，卡片式单选，个人订阅标「仅平台」）、API Key（已保存时只读显示脱敏值，点「更换」才可输入新值，「更换」与「取消更换」都在输入框内；输入框用普通文本框而非密码框，便于核对粘贴的长 Key，已保存的 Key 仍不回显；保存前 `Modal.confirm`；测试失败时字段旁提示）；订阅方式下显示两个账号的登录状态与登录命令。
- 右「模型与用量」卡：两个模型（antd `AutoComplete`：预填当前服务商的推荐模型，可手动输入，首次展开时请求 `/models`，切换服务商清空已取列表并把没改过的推荐模型换成新服务商的，列表中推荐模型标「推荐」）、两个思考强度（分段选择，空值显示为「中」）、最大搜索次数（加减步进）。
- 卡片不加副标题；API Key、解析模型、翻译模型的说明以小字跟在字段标题后面；两张卡片等高。
- 底部保存条：只在有未保存的修改时出现，提供撤销与保存；缺 Key 时保存不可用。

## Risks / Trade-offs

- [生产升级后 AI 不可用，直到平台租户填写配置] → 项目未正式使用，可接受；PR 说明与部署文档写明上线后第一步是在「业务设置 → AI 设置」填写并测试。
- [Key 以明文出现在后端到编排服务的请求体] → 仅在 Compose 内部网络，编排服务不映射宿主端口；新增令牌校验；两端不记录请求体，`AiCallConfig` / `AiConfig` 的字符串表示脱敏；测试覆盖失败路径的日志与回调不含 Key。
- [编排服务不再在启动时发现配置错误] → 改为保存时校验 + 测试连接；任务失败原因写明是哪个字段。
- [订阅方式的租户限制只在后端] → 编排服务在内部网络且有令牌，后端是唯一调用方；规格要求后端校验，测试覆盖普通租户保存 `subscription` 被拒。
- [stub 联调多一步配置] → 文档说明；不为 stub 特殊放行，避免生产误用。

## Migration Plan

1. 合并后随版本部署：Flyway `V1.3.17` 建表；编排服务与后端同版本上线（请求体新增 `ai` 字段与令牌校验需要两端同时更新，部署脚本一次性替换三个镜像）。
2. 上线后以平台账号进入「业务设置 → AI 设置」填写服务商与 Key，点测试连接确认通过，再试一次询盘解析。
3. 可选：从服务器 `.env` 删除 7 个已不读取的 AI 配置项。
4. 回退：回退镜像到上一版本即可恢复读取 `.env` 的行为（服务器 `.env` 未删这些项时立即可用）；`sys_ai_config` 表保留或执行回退 SQL 删除，不影响旧版本。
