## Context

AI 编排服务是单个 Python 脚本（`scripts/ai-orchestrator/server.py`）。三类任务（询盘解析、型号描述翻译、联系人名音译）最终都经过同一个 `call_api`，它直接使用 Anthropic SDK：结构化输出用 `output_config.format`；联网搜索用服务端工具 `web_search_20260209`，遇到 `pause_turn` 续传；思考强度写死为 `medium`。Key 依靠 SDK 自动读取环境变量 `ANTHROPIC_API_KEY`。同一镜像还包含 stub，由 `AI_ORCHESTRATOR_MODE` 在 `docker-entrypoint.sh` 里选择。需求与范围见 proposal.md 和 `specs/ai-platform/model-providers/spec.md`。

OpenAI 现状（2026-10 官方文档）：推荐走 Responses API；`web_search` 是平台托管工具；结构化输出用 `text.format`（`json_schema` + `strict: true`）；图片用 `input_image`，PDF 用 `input_file`（`file_data` 传 base64，带视觉能力的模型会同时提取文字和页面图像）；思考强度为 `reasoning.effort`，`gpt-6.1-sol` 支持 `low` 到 `max`、默认 `medium`，`gpt-6-luna` 额外支持 `none`；`max_tool_calls` 限制单次响应内托管工具的调用次数。联网搜索每千次 $10，搜索返回的内容按模型价格计入输入 token。

## Goals / Non-Goals

**Goals:**
- 三类任务的业务逻辑（提示词、输出结构、回调格式、错误处理）只写一份，服务商差异集中在「把一次请求发给某家服务商，拿回 JSON」这一层。
- Claude 路径的请求参数除 Key 来源和思考强度来自配置外，与现在完全一致。

**Non-Goals:**
- 不做通用的多服务商框架或插件机制，只支持 `claude`、`openai` 两个取值。
- 不做 DeepSeek；不做按任务分别选服务商；不做失败时自动切换到另一家服务商。
- 不改 stub，不改后端与编排服务之间的任务契约。

## Decisions

### 1. 仍是单个脚本，按服务商拆成两个调用函数

`call_api` 改为按 `PROVIDER` 分派到 `_call_claude(...)` 或 `_call_openai(...)`，两者签名相同：系统提示词、正文、附件（已读好的 `(内容, 扩展名)`）、JSON 结构、是否联网搜索、模型、思考强度，返回解析后的 dict。附件转成各家的内容块格式，也放在各自函数里。

- 备选：拆成 `providers/claude.py`、`providers/openai.py` 加抽象基类。只有两家服务商，单一用途的抽象没有收益，还要改 Dockerfile 的复制范围，所以不拆。新增量估计约 120 行。

### 2. OpenAI 使用 Responses API，不用 Chat Completions

只有 Responses API 同时支持托管 `web_search`、`input_file`（PDF）和严格结构化输出；Chat Completions 在 GPT-6 上使用工具受限（`gpt-6-luna` 只有 `reasoning_effort=none` 时才能调用函数）。请求要点：

- 用 `instructions` 传系统提示词，`input` 为一条 user 消息：`input_text`、`input_image`（`data:` URL）、`input_file`（`filename` + `file_data`）；
- 解析任务带 `tools=[{"type": "web_search"}]` 和 `max_tool_calls=AI_ORCHESTRATOR_MAX_WEB_SEARCHES`；翻译任务不带工具；
- `reasoning={"effort": ...}`；`text.format` 设为 `json_schema`、`strict: true`；`max_output_tokens=64000`；
- 非流式调用，SDK 超时沿用 `AI_ORCHESTRATOR_TIMEOUT_SECONDS`。
- 备选：用 `background` 模式加轮询，应对长时间运行的请求。现有 900 秒超时与 Claude 路径一致，后端看门狗也按此设置，暂不引入。

### 3. 结构化输出的结构按服务商转换

Claude 的结构化输出允许字段非必填；OpenAI 严格模式要求每个 object 的所有字段都列在 `required` 中，可选字段只能用「类型或 `null`」表示。转换规则：发给 OpenAI 前，把原 `required` 之外的字段改成可空并全部列为必填，同时补上 `additionalProperties: false`；收到结果后递归删除值为 `null` 的字段。这样回调给后端的 JSON 与 Claude 一致（未给出的可选字段不出现），满足规范「可选字段未给出」场景。原 `OUTPUT_SCHEMA`、`TRANSLATE_SCHEMA` 保持一份，不为 OpenAI 单独维护。

### 4. 配置集中读取，启动时校验

模块顶部读取全部配置：`PROVIDER`、`AI_ORCHESTRATOR_API_KEY`、两个模型（留空时按服务商查推荐默认值表）、两个思考强度。在 `__main__` 中调用一次校验：服务商或思考强度取值不合法时打印错误并以非 0 退出，容器因此反复重启，`docker ps` 和日志能直接看到原因；Key 为空只打印警告，与现在行为一致。启动日志列出服务商、两个模型和两个思考强度。

- 思考强度只校验是否属于 `low`、`medium`、`high`、`xhigh`、`max`，不按模型细分：两家当前推荐模型都支持这五个取值，用户指定的其他模型如果不支持，由服务商返回错误，任务回调失败并带上原因。
- Key 只有一个变量：同一实例只用一家服务商，不需要同时保存两家的 Key。代价是切换服务商时要一并更换 Key；填错了服务商的 Key 时，服务商返回认证错误，任务回调失败并带上原因。
- Key 显式传给 SDK 客户端（`api_key=`），不再依赖 SDK 自动读取 `ANTHROPIC_API_KEY`、`OPENAI_API_KEY`，避免旧变量或其他环境变量仍在环境里时被悄悄读到。

### 5. 错误归一

两家 SDK 的超时异常（`anthropic.APITimeoutError`、`openai.APITimeoutError`）统一回调「AI 处理超过 N 秒未完成」；OpenAI 响应状态为 `incomplete`（输出超长）时，回调与 Claude `max_tokens` 相同的提示；返回内容为 refusal 时，回调「模型拒绝处理这条内容」；其他异常沿用现有逻辑，截取前 500 个字符作为失败原因。日志打印两家的 token 用量和联网搜索次数（OpenAI 按输出中 `web_search_call` 项计数）。

### 6. 依赖

`requirements.txt` 新增 `openai`，固定一个实施时已发布满两周的版本。镜像体积和构建时间小幅增加。

## Risks / Trade-offs

- [OpenAI 的 `max_tool_calls` 统计全部托管工具调用，包括打开网页和页内查找，不只是搜索] → 同样的上限下，OpenAI 实际搜索次数可能比 Claude 少。文档注明这一差异；不够用时调大 `AI_ORCHESTRATOR_MAX_WEB_SEARCHES`。
- [OpenAI 没有 Claude 那样的服务端拒答回退] → refusal 直接回调失败，用户可以重试或改用 Claude。
- [两家的解析质量、费用不同，本次没有对比数据] → 默认服务商仍是 Claude；切到 OpenAI 前用真实询盘对比。没有 OpenAI Key 之前，只能用假客户端验证请求参数。
- [变量改名会让旧配置失效] → 迁移步骤见下文；旧变量名不再被读取，启动警告和失败原因会直接指出新变量名。
- [严格模式转换后的结构与原结构存在细微差异，比如可空字段] → 收到结果后统一删除 null 字段，用单元测试覆盖「可选字段缺失」「嵌套数组里的可选字段」两种情况。

## Migration Plan

1. 实施与测试在 `release/1.3.0` 的功能分支完成。本机 `.env` 改名：`ANTHROPIC_API_KEY` → `AI_ORCHESTRATOR_API_KEY`，`AI_ORCHESTRATOR_MODEL` → `AI_ORCHESTRATOR_PARSE_MODEL`，并补上新增项。
2. 合并到 main 之前（按 `08-生产发版.md` 的检查）：在生产 `.env` 新增 `AI_ORCHESTRATOR_API_KEY`（取原 `ANTHROPIC_API_KEY` 的值）、`AI_ORCHESTRATOR_PARSE_MODEL`、`AI_ORCHESTRATOR_PROVIDER`、两个思考强度变量。**旧的 `ANTHROPIC_API_KEY`、`AI_ORCHESTRATOR_MODEL` 先保留**：新版本不读取它们，部署预检也不检查多出来的配置项。
3. 上线后核对 AI 编排启动日志（服务商、模型、思考强度）并做一次真实调用，确认无误后再删除生产 `.env` 中的两个旧变量，同步本机副本。
4. 回滚：Actions 重新部署上一版本即可，旧变量在第 3 步前仍在，旧版本照常读取。
