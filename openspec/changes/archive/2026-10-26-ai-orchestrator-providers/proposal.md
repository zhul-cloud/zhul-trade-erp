## Why

AI 编排服务只能调用 Anthropic（Claude），换服务商就要改代码；配置变量命名也不统一：Key 叫 `ANTHROPIC_API_KEY`，解析模型叫 `AI_ORCHESTRATOR_MODEL`，看名字分不清作用，思考强度也写死在代码里（`medium`）。现在需要能按价格与效果在 Claude 和 OpenAI 之间切换，并让模型和思考强度都能按任务配置。

## What Changes

- **新增服务商切换**：新增 `AI_ORCHESTRATOR_PROVIDER`，可选 `claude`（默认）、`openai`。`AI_ORCHESTRATOR_MODE` 含义不变（`api` 真实调用 / `stub` 假数据）。
- **接入 OpenAI**：询盘解析、型号描述翻译、联系人名音译三类任务都可以用 OpenAI（Responses API）完成，能力与 Claude 一致：图片和 PDF 附件、联网搜索验证型号、按约定的 JSON 结构输出、思考强度。
- **BREAKING 变量改名**，不保留旧名：
  - `ANTHROPIC_API_KEY` → `AI_ORCHESTRATOR_API_KEY`：只有一个 Key 变量，填当前所选服务商的 Key（同一时间只用一家，切换服务商时一并更换 Key）
  - `AI_ORCHESTRATOR_MODEL` → `AI_ORCHESTRATOR_PARSE_MODEL`
  - `AI_ORCHESTRATOR_TRANSLATE_MODEL` 名称不变。
- **按任务配置思考强度**：新增 `AI_ORCHESTRATOR_PARSE_EFFORT`、`AI_ORCHESTRATOR_TRANSLATE_EFFORT`，可选 `low`、`medium`、`high`、`xhigh`、`max`，默认都是 `medium`。
- **模型留空时使用推荐默认值**（按服务商）：

  | 服务商 | 询盘解析 | 翻译 |
  | --- | --- | --- |
  | `claude` | `claude-opus-5-5` | `claude-haiku-5-5` |
  | `openai` | `gpt-6.1-sol` | `gpt-6-luna` |

- **配置错误提前暴露**：服务商取值、思考强度取值不合法时，服务启动即报错退出；Key 为空时，启动打印警告，任务回调失败并说明缺少 `AI_ORCHESTRATOR_API_KEY`。
- 不包含：DeepSeek（不支持 PDF、没有内置联网搜索，本次不做）；按任务分别选服务商；后端与前端改动。

## Capabilities

### New Capabilities

- `ai-platform/model-providers`：AI 编排服务调用哪家模型服务商、用哪个模型和多大思考强度，以及相关配置的取值、默认值与错误处理。

### Modified Capabilities

（无。后端与编排服务之间的任务契约 `ai-platform/task-orchestration` 不变。）

## Impact

- AI 编排服务：`scripts/ai-orchestrator/server.py`（按服务商分开调用；Claude 路径行为不变）、`requirements.txt`（新增 `openai`）、`docker-entrypoint.sh`、README。
- 配置与部署：`.env.example`、`docker-compose.yaml`、`scripts/docker-dev.sh`、`scripts/docker-dev.ps1`、`scripts/dev-up.sh`；部署文档 `docs/06-部署运维/` 的 01、02、04、05 篇，项目 `README.md`。
- 生产：变量改名属于新增配置项，按 `docs/06-部署运维/08-生产发版.md`，合并到 main 之前要先把生产 `.env` 的 `ANTHROPIC_API_KEY` 值迁到 `AI_ORCHESTRATOR_API_KEY`，并补齐其他新增项；本机 `.env` 同步改名。
- 后端、前端、数据库、接口：无改动。
