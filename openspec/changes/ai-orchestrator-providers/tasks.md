## 1. 配置读取与校验

- [x] 1.1 在 `server.py` 顶部集中读取 `AI_ORCHESTRATOR_PROVIDER`（默认 `claude`）、`AI_ORCHESTRATOR_API_KEY`、`AI_ORCHESTRATOR_PARSE_MODEL`、`AI_ORCHESTRATOR_TRANSLATE_MODEL`、`AI_ORCHESTRATOR_PARSE_EFFORT`、`AI_ORCHESTRATOR_TRANSLATE_EFFORT`（默认 `medium`），模型留空时按服务商取推荐默认值；删除对 `ANTHROPIC_API_KEY`、`AI_ORCHESTRATOR_MODEL` 的读取。验证：`grep` 确认 `server.py` 中不再出现这两个旧变量名
- [x] 1.2 在 `__main__` 中校验服务商与思考强度：取值非法时打印变量名与可选值并以非 0 退出；Key 为空时只打印警告；启动日志列出服务商、两个模型、两个思考强度。验证：分别用 `AI_ORCHESTRATOR_PROVIDER=deepseek`、`AI_ORCHESTRATOR_TRANSLATE_EFFORT=fast` 启动脚本，确认退出码非 0 且提示符合规范；正常配置下启动日志内容正确

## 2. Claude 调用改造（行为不变）

- [x] 2.1 把现有 `call_api` 的 Anthropic 部分整理为 `_call_claude`：Key 显式传给客户端，思考强度取自配置，附件转换为 Claude 内容块的逻辑一并移入；其余参数（结构化输出、联网搜索工具、`pause_turn` 续传、Haiku 不带 `fallbacks`）保持原样。验证：用假 `anthropic` 客户端拦截请求，三类任务的请求参数与改造前一致，只有 `output_config.effort` 跟随配置变化
- [x] 2.2 `call_api` 按 `PROVIDER` 分派；Key 为空时抛出的失败原因写明缺少的变量名；超时异常统一回调「AI 处理超过 N 秒未完成」。验证：假客户端测试覆盖 Key 为空、超时两种情况，回调的失败原因符合规范

## 3. 接入 OpenAI

- [x] 3.1 `requirements.txt` 新增 `openai`，固定一个已发布满两周的版本。验证：`pip install -r scripts/ai-orchestrator/requirements.txt` 成功，`import openai` 正常
- [x] 3.2 实现结构转换：发给 OpenAI 的结构把非必填字段改为「类型或 null」并全部列为必填，补 `additionalProperties: false`；收到结果后递归删除 null 字段。验证：单元测试覆盖 `OUTPUT_SCHEMA` 转换后每个 object 的 `required` 包含全部字段，以及「顶层可选字段缺失」「嵌套数组中可选字段缺失」两种结果的清理
- [x] 3.3 实现 `_call_openai`：Responses API，`instructions` + 一条 user 消息（`input_text`、图片 `input_image`、PDF `input_file`），解析任务带 `web_search` 与 `max_tool_calls`，`reasoning.effort` 取自配置，`text.format` 为严格 `json_schema`，`max_output_tokens=64000`；`incomplete` 状态和 refusal 分别回调对应失败原因；日志打印 token 用量与 `web_search_call` 次数。验证：用假 `openai` 客户端拦截请求，核对解析（含图片和 PDF 附件）与两类翻译的请求参数，以及 `incomplete`、refusal、正常返回三种响应的处理结果
- [x] 3.4 更新 `scripts/ai-orchestrator/README.md`：服务商、Key、模型、思考强度的变量表与推荐默认值，OpenAI 的 `max_tool_calls` 统计范围与 Claude 的差异。验证：表中变量名与 `server.py` 中读取的一致

## 4. 部署配置与开发脚本

- [x] 4.1 `.env.example` 的 AI 编排段改为新变量（含注释与推荐默认值说明），`docker-compose.yaml` 的 `ai-orchestrator` 服务传入新变量、去掉旧变量。验证：`docker compose --profile app config` 解析成功，`ai-orchestrator` 的环境变量中只有新变量
- [x] 4.2 `scripts/docker-dev.sh`、`scripts/docker-dev.ps1`：首次生成 `.env` 时 `AI_ORCHESTRATOR_API_KEY` 为空则设为 stub；`MODE=api` 但 Key 为空时给出警告并写明变量名。`scripts/dev-up.sh` 的提示文字改为新变量名。验证：在临时目录用空 `.env` 跑生成逻辑，结果为 stub；`MODE=api` 且不填 Key 时，警告里出现 `AI_ORCHESTRATOR_API_KEY`
- [x] 4.3 更新部署文档 `docs/06-部署运维/` 的 01、02、04、05 篇和项目 `README.md` 中的变量名与说明，04 篇加入服务商切换、推荐模型与思考强度说明。验证：`git grep -n "ANTHROPIC_API_KEY\|AI_ORCHESTRATOR_MODEL\b"` 在仓库现行文件中（不含 openspec 归档与本变更文档）无结果
- [x] 4.4 本机 `.env` 按新变量名迁移（旧值保留到新名下）。验证：重建本机 `ai-orchestrator` 后启动日志显示 `claude`、两个推荐模型和 `medium`，且没有缺少 Key 的警告（本机未填 Key 时，警告应指向 `AI_ORCHESTRATOR_API_KEY`）

## 5. 集成验证

- [x] 5.1 本机 Docker 重建 `ai-orchestrator`，分别以 `claude`、`openai` 启动，确认启动日志、健康检查正常，Key 为空时任务回调失败的原因符合规范。验证：`docker compose logs ai-orchestrator` 与回调结果截取
- [ ] 5.2 经用户确认后做真实调用（会产生费用）：Claude 跑一次翻译（Haiku）和一次询盘解析；有 OpenAI Key 时再各跑一次 OpenAI。验证：回调成功，后端能正常写入结果，日志中的 token 用量与搜索次数合理（2026-10-25 用户决定跳过，不做真实调用，上线后在生产页面实测）
- [x] 5.3 `openspec validate ai-orchestrator-providers --strict` 通过；本次所有改动文件均为 LF。验证：命令输出与按字节检查 CR 的结果

## Workflow follow-up

- 合并到 main 之前，按 design.md 迁移计划第 2 步补齐生产 `.env`（保留旧变量），并在 PR 说明中列出新增与改名的配置项。
- 上线后核对生产 AI 编排的启动日志，做一次真实调用，确认无误后删除生产 `.env` 中的旧变量并同步本机副本。
- 满足评审要求后归档本变更，并确认 `openspec/specs/ai-platform/model-providers/spec.md` 已生成。
