# Tasks

## 1. 数据库

- [x] 1.1 新增迁移 `V1.3.17__sys_ai_config.sql`：建表 `sys_ai_config`（字段见 design.md 决策 1，`tenant_id` 唯一索引、`status` 索引，所有字段带 COMMENT 并列出枚举值）；新增回退 SQL `sql/build/rollback/V1.3.17__sys_ai_config_rollback.sql`（删表）。验证：本机后端启动后表存在；回退 SQL 在测试库执行后表被删除

## 2. 后端：AI 配置模块

- [x] 2.1 实体 `SysAiConfigDO`、Mapper、请求 DTO 与响应 VO（Key 只返回 `hasKey` 与脱敏值 `sk-ant-••••••••••••••••abcd`）、`AiCallConfig`（补全默认值后的完整配置，`toString()` 脱敏 Key）。验证：单元测试覆盖脱敏规则（短 Key、空 Key）与 `toString()` 不含明文
- [x] 2.2 `AiConfigService`：按 `TenantContext` 查询与保存（upsert）；校验服务商、思考强度、最大搜索次数 1–100、非平台租户不得选 `subscription`；Key 空串不修改，非空则加密保存；`resolveForCurrentTenant()` 返回 `AiCallConfig`，未配置（无行，或 API 方式无 Key）时抛 `AiNotConfiguredException`。验证：集成测试覆盖两个租户互不可见、平台配置不被租户继承、非法取值、普通租户保存订阅被拒、Key 留空保持原值、库中 Key 为 `v2:` 密文
- [x] 2.3 `AiConfigController`（`/api/v1/system/ai-config`）：`GET`、`PUT`、`POST /test`、`POST /models`、`GET /subscription-status`，权限见 design.md 决策 5；`/subscription-status` 非平台租户拒绝。验证：接口测试覆盖权限与平台租户限制，响应中不含 Key 明文

## 3. 后端：提交任务携带配置

- [x] 3.1 `AiTaskService.createAndSubmit` 先 `resolveForCurrentTenant()` 再创建任务，配置快照传给 `submit`；`AiOrchestratorClient.submit` 增加 `AiCallConfig` 参数，请求体加 `ai` 字段；新增 `test(AiCallConfig)`、`models(AiCallConfig)` 与 `status()` 三个同步调用，均带 `X-AI-Callback-Token`。验证：单元测试断言请求体包含 `ai` 且日志不含 Key；未配置时抛异常且没有插入 `ai_task`
- [x] 3.2 `ContactNameTranslations.submit` 捕获 `AiNotConfiguredException` 后记日志跳过。验证：测试未配置租户保存商机成功、未创建翻译任务；询盘解析与描述翻译在未配置时返回「AI 未配置」提示

## 4. 编排服务

- [x] 4.1 `server.py`：删除读取 7 个环境变量的模块常量，新增 `AiConfig`（解析、默认值、校验，`__repr__` 脱敏）；解析与翻译流程、`call_api`、`_call_claude`、`_call_openai` 按任务配置调用；配置不合法时任务回调失败；启动校验只保留部署项。验证：`test_server.py` 覆盖两家服务商各自的默认模型、指定模型、非法思考强度回调失败、缺 Key 回调失败且原因不含 Key
- [x] 4.2 `subscription.py` 改为使用任务携带的服务商、模型、思考强度与搜索上限。验证：`test_subscription.py` 原有用例改为传入配置后全部通过
- [x] 4.3 所有接口校验 `X-AI-Callback-Token`（`hmac.compare_digest`），错误返回 401；stub 服务同样校验并忽略 `ai` 字段。验证：测试覆盖缺失、错误、正确三种令牌
- [x] 4.4 新增 `POST /test`（API 方式查询两个模型，订阅方式返回登录状态）与 `GET /status`（`codex login status`、`claude auth status`，10 秒超时，只返回是否登录）。验证：测试用桩替代 SDK 与 CLI，覆盖 Key 无效、模型不存在、全部通过、CLI 超时
- [x] 4.5 新增 `POST /models`：API 两家按 Key 列出模型（OpenAI 过滤非对话模型）、Codex 订阅用 SDK `models()`、Claude 订阅与无 Key 返回默认模型。验证：测试用桩覆盖四种情况、OpenAI 过滤结果、Key 无效时错误不含 Key
- [x] 4.6 `docker-entrypoint.sh` 非 `stub` 一律启动真实服务；`.env.example`、`docker-compose.yaml` 删除 7 个 AI 业务配置项，`AI_ORCHESTRATOR_MODE` 注释改为 `api` / `stub`。验证：`docker compose config` 中 ai-orchestrator 不再有这 7 项；本机 `.env` 为 `subscription` 时容器正常启动真实服务

## 5. 前端

- [x] 5.1 AI 设置表单（最初做在系统参数页的「AI」页签，后按 7.x 改为独立页面 `pages/system/ai-settings`）：字段、平台租户才显示运行方式、Key 脱敏显示与更换前二次确认、模型占位符随服务商显示默认值、保存；`service.ts` 增加四个接口。验证：Jest 覆盖平台租户与普通租户的显示差异、Key 留空不提交；Biome、tsc、`npx antd lint` 通过
- [x] 5.2 两个模型输入框改为 `AutoComplete`：首次展开请求模型列表、切换服务商清空、默认模型标记、失败提示后仍可手动输入。验证：Jest 覆盖手动输入值原样保存、切换服务商后重新请求
- [x] 5.3 「测试连接」逐项显示结果；平台租户显示订阅登录状态与登录命令。验证：本机浏览器实测（见 6.2）（BrowserOS 实测：假 Key 测试连接显示「API Key：Key 无效或已失效」；平台租户订阅登录状态卡片显示两个 CLI 未登录及登录命令）

## 6. 文档与验证

- [x] 6.1 改写 `docs/06-部署运维/04-AI编排服务.md`（配置改在系统中维护、`.env` 只剩部署项、上线后先配置平台租户、stub 联调也需填写配置、订阅只供平台租户）与 `scripts/ai-orchestrator/README.md`。验证：文档中不再出现已删除的 7 个变量作为配置说明
- [x] 6.2 本机端到端：平台租户与测试租户分别配置（测试租户只能看到 API 方式）；无 Key 时下拉框只有默认模型、填写 Key 后能获取列表（无真实 Key 时以 Key 无效的提示验证错误路径）；测试连接显示 Key 无效的情况；未配置租户解析询盘提示未配置、保存商机成功；`stub` 模式下已配置租户解析询盘成功；平台租户 AI 页签显示订阅登录状态；后端与编排服务日志中搜索 Key 无结果（结果：接口端到端全部符合；页面实测平台租户视图、测试连接、模型下拉、保存二次确认；普通租户页面差异由接口端到端与组件测试覆盖，页面未以租户账号登录实看）
- [x] 6.3 后端 `mvn test`、编排服务测试、前端 lint/test 全部通过（已知 `ImageCompressorTest` 既有失败除外）；`openspec validate ai-config-per-tenant --strict` 通过，改动文件为 LF（结果：后端 1086 个仅既有 ImageCompressorTest 失败，另 3 个契约测试因测试库刚重建失败、复跑通过；编排服务 41、前端 135 全部通过）

## 7. 独立菜单与页面重做（2026-10-29 用户否决系统参数页签方案后追加）

- [x] 7.1 用 OpenPencil 出原型 `docs/03-产品原型/00-用户域/07-AI设置/AI设置.op`（S1 平台 API 已就绪、S2 平台个人订阅、S3 普通租户未配置、S4 测试连接失败、S5 模型下拉），用户确认后再开发。验证：用户 2026-10-29 确认
- [x] 7.2 迁移 `V1.3.18__ai_settings_menu.sql`：菜单「业务设置 → AI 设置」（100118，`/system/ai-settings`）与按钮「编辑 AI 设置」（110214，`system:ai-config:edit`），加进所有已有套餐与内置管理员角色；回退 SQL；`AiConfigController` 改为菜单 + 编辑权限；配置接口返回最后修改人与时间。验证：`AiConfigContractTest` 覆盖无菜单拒绝查看、只有菜单能查看不能保存
- [x] 7.3 前端独立页面 `pages/system/ai-settings`（状态卡、服务商与账号卡、模型与用量卡、底部保存条）、路由、`access.ts`、菜单文案；系统参数页移除 AI 页签与相关代码；提示文案统一为「业务设置 → AI 设置」。验证：Jest 覆盖普通/平台租户差异、未修改不能保存、Key 留空不提交、下拉重新获取、未配置引导、测试失败提示；lint、tsc、`npx antd lint` 通过
- [x] 7.4 本机浏览器实测新页面，与原型对照（结果：平台租户在 1440 宽度实测状态卡、两张卡片等高、模型预填与切换服务商替换、保存条按需出现与撤销、更换 Key 普通文本框与取消更换、Key 脱敏显示；普通租户的页面差异（无运行方式、无订阅状态）由组件测试与接口契约测试覆盖，未以租户账号登录实看。追加修改：用户确认后去掉卡片副标题、说明移到字段标题后、服务商移到运行方式上方、左卡改名「服务商与账号」、Key 脱敏改为前 7 位 + 16 点 + 后 4 位，均先改原型 S1–S7）

## Workflow follow-up

- 归档本变更，PR 发往 `release/1.3.0`。PR 说明写明：迁移 `V1.3.17`（建表）、`V1.3.18`（菜单与权限）及回退 SQL；`.env.example` 删除 7 项（生产 `.env` 无需改动）；上线后 AI 不可用，需先以平台账号在「业务设置 → AI 设置」填写并测试连接；后端与编排服务需同版本上线。
