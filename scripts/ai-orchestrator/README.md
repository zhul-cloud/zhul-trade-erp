# AI 编排服务

为询盘解析、型号描述翻译、联系人名音译提供统一异步 webhook。后端提交 `POST /skills/{skill_id}/run`，立即得到 `202 {job_id}`；任务结束后按现有契约回调 `success` 或 `failed`，前后端不依赖所用服务商或认证模式。

| `AI_ORCHESTRATOR_MODE` | 实现 |
| --- | --- |
| `api`（默认） | 官方 Anthropic/OpenAI Python SDK，读取 `AI_ORCHESTRATOR_API_KEY`，按 API 用量计费 |
| `subscription` | 部署者本人通过官方 CLI 登录，`openai` 使用官方 Codex Python SDK，`claude` 使用原版 Claude Code CLI |
| `stub` | 独立占位脚本，固定假数据，仅供联调 |

Docker 镜像包含三种模式，通过根目录 `.env` 切换。订阅登录、专用目录与可选持久化、套餐适用范围、附件与搜索限制见 [AI 编排部署说明](../../docs/06-部署运维/04-AI编排服务.md)。Windows 的订阅执行请使用 Docker；不要挂载宿主机个人 CLI 目录，不提供账号共享或代登录服务。

## API 本机运行

```bash
pip install -r scripts/ai-orchestrator/requirements.txt
python scripts/ai-orchestrator/server.py
```

Key 通过当前终端的 `AI_ORCHESTRATOR_API_KEY` 提供。监听端口默认 `8899`（`AI_STUB_PORT`）；服务商 `AI_ORCHESTRATOR_PROVIDER=claude|openai`，默认 `claude`。回调共享令牌 `AI_TASK_CALLBACK_TOKEN` 必须与后端一致，不能将服务端口公开到互联网。

两种任务分别由 `AI_ORCHESTRATOR_PARSE_MODEL` / `AI_ORCHESTRATOR_TRANSLATE_MODEL` 和对应 `*_EFFORT` 控制，effort 可选 `low/medium/high/xhigh/max`，默认 `medium`。模型默认值（api 与 subscription 模式相同）：Claude 为 `claude-opus-5-5` / `claude-haiku-5-5`，OpenAI 为 `gpt-6.1-sol` / `gpt-6-luna`。

`AI_ORCHESTRATOR_MAX_WEB_SEARCHES=20`，`AI_ORCHESTRATOR_TIMEOUT_SECONDS=900`。订阅使用保守的操作事件阈值，达到阈值会终止并回调失败，成功任务须少于该阈值；超时终止整个 Linux 任务进程组。一个实例同时只执行一个订阅任务，忙碌时立即失败，调用方可稍后重试。认证失败不会降级 API。

## 实现与验证

`server.py` 保留任务路由、API 客户端和后端回调。`subscription.py` 负责专用环境、短生命周期 worker、官方 SDK/CLI、搜索事件计数及严格结果校验。图片转 PNG，PDF（含扫描件）逐页渲染；不静默忽略附件。附件通过后端签发临时 URL 下载，兼容旧版共享目录路径。

`skills/inquiry-parser/SKILL.md` 与 `skills/order-splitter/SKILL.md` 作为任务说明注入模型；CLI 不加载外部 hooks、MCP、插件或个人项目指令。Codex 禁用 shell 与不需要的工具，Claude 仅开放搜索工具；图片直接输入，不授予文件读取权限。API 的服务商原有结构化输出、托管搜索、Claude `pause_turn` 续传/拒答兜底保持不变。

离线测试（假客户端/真实附件，不调用模型、不产生模型费用）：

```bash
python -B -m unittest discover -s scripts/ai-orchestrator -v
```

Linux 进程组回归在构建出的镜像内运行同一命令（测试目录为 `/app/ai-orchestrator`）。SDK/CLI 的登录状态与 thread 构造可用空 HOME 验证；这不代替实际账号授权后的端到端模型验收。
