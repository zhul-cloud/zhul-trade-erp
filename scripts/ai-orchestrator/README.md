# AI 编排服务

为询盘解析、型号描述翻译、联系人名音译提供统一异步 webhook。后端提交 `POST /skills/{skill_id}/run`，立即得到 `202 {job_id}`；任务结束后按现有契约回调 `success` 或 `failed`，前后端不依赖所用服务商或认证模式。

服务商、API Key、模型、思考强度、搜索上限、运行方式（API / 个人订阅）都由后端随每个任务放在请求体 `ai` 字段中传入，来自租户在「业务设置 → AI 设置」中维护的配置；本服务不读取这些环境变量，同一实例可同时为不同租户使用不同服务商。另有同步接口 `POST /test`（测试连接）、`POST /models`（模型下拉列表）、`GET /status`（订阅登录状态）。所有接口校验请求头 `X-AI-Callback-Token` 与 `AI_TASK_CALLBACK_TOKEN` 一致，不一致返回 401。

| `AI_ORCHESTRATOR_MODE` | 实现 |
| --- | --- |
| `api`（默认） | 真实服务：任务携带的运行方式为 API 时用官方 Anthropic/OpenAI Python SDK；为订阅时 `openai` 用官方 Codex Python SDK、`claude` 用原版 Claude Code CLI（只供平台租户） |
| `stub` | 独立占位脚本，固定假数据，仅供联调 |

订阅登录、专用目录与持久化、套餐适用范围、附件与搜索限制见 [AI 编排部署说明](../../docs/06-部署运维/04-AI编排服务.md)。Windows 的订阅执行请使用 Docker；不要挂载宿主机个人 CLI 目录，不提供账号共享或代登录服务。

## 本机运行

```bash
pip install -r scripts/ai-orchestrator/requirements.txt
python scripts/ai-orchestrator/server.py
```

监听端口默认 `8899`（`AI_STUB_PORT`）；共享令牌 `AI_TASK_CALLBACK_TOKEN` 必须与后端一致，不能将服务端口公开到互联网。`AI_ORCHESTRATOR_TIMEOUT_SECONDS` 为单次调用超时，默认 900 秒。

模型默认值（API 与订阅相同，后端也会先补全）：Claude 为 `claude-opus-5-5` / `claude-haiku-5-5`，OpenAI 为 `gpt-6.1-sol` / `gpt-6-luna`；思考强度可选 `low/medium/high/xhigh/max`，默认 `medium`；单次解析最大搜索次数 1–100，默认 20。任务携带的配置不合法（如不支持的服务商、思考强度，API 方式缺 Key）时只让该任务回调失败，不影响其他任务。

订阅使用保守的操作事件阈值，达到阈值会终止并回调失败，成功任务须少于该阈值；超时终止整个 Linux 任务进程组。一个实例同时只执行一个订阅任务，忙碌时立即失败，调用方可稍后重试。认证失败不会降级 API。

## 实现与验证

`server.py` 负责任务路由、按任务配置调用 API 客户端（每次新建客户端，不跨租户复用）、测试连接与模型列表、令牌校验和后端回调；`AiConfig` 的字符串表示与错误信息都对 Key 脱敏。`subscription.py` 负责专用环境、短生命周期 worker、官方 SDK/CLI、搜索事件计数、严格结果校验，以及 CLI 登录状态与 Codex 账号模型列表。图片转 PNG，PDF（含扫描件）逐页渲染；不静默忽略附件。附件通过后端签发临时 URL 下载，兼容旧版共享目录路径。

`skills/inquiry-parser/SKILL.md` 与 `skills/order-splitter/SKILL.md` 作为任务说明注入模型；CLI 不加载外部 hooks、MCP、插件或个人项目指令。Codex 禁用 shell 与不需要的工具，Claude 仅开放搜索工具；图片直接输入，不授予文件读取权限。API 的服务商原有结构化输出、托管搜索、Claude `pause_turn` 续传/拒答兜底保持不变。

离线测试（假客户端/真实附件，不调用模型、不产生模型费用）：

```bash
python -B -m unittest discover -s scripts/ai-orchestrator -v
```

Linux 进程组回归在构建出的镜像内运行同一命令（测试目录为 `/app/ai-orchestrator`）。SDK/CLI 的登录状态与 thread 构造可用空 HOME 验证；这不代替实际账号授权后的端到端模型验收。
