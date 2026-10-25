# AI 编排服务（真实实现，基于 Claude / OpenAI API）

跟 `scripts/ai-orchestrator-stub/` 不是一回事——那个是免费的假数据占位脚本，
这个是真的会调用 AI、产生真实费用的实现，通过官方 Python SDK 调用 Claude（Anthropic API）
或 OpenAI（Responses API），由 `AI_ORCHESTRATOR_PROVIDER` 选择，鉴权用 `AI_ORCHESTRATOR_API_KEY`，
不需要安装 Claude Code CLI。

对应 `openspec/changes/add-inquiry-management/design.md` 决策3的通用契约，跟
zhul-erp-backend 之间走同一套 webhook 接口，两边代码都不用改，直接把这个服务
指向和 stub 一样的端口/回调地址即可切换。换服务商也不影响后端，回调的结果结构一致。

## 前置条件

1. Python 3.10+，`pip install -r requirements.txt`
2. 环境变量 `AI_ORCHESTRATOR_API_KEY`：填当前服务商的 Key（Claude 在 Anthropic Console 创建，
   OpenAI 在 OpenAI Platform 创建）。同一时间只用一家，切换服务商时一并更换 Key
3. `skills/` 目录下的 `inquiry-parser/SKILL.md`、`order-splitter/SKILL.md`
   是从团队现有的"烛龙询盘助手"复制过来的，改动请求先跟维护这套 skill 的人对齐

## 运行

Docker 部署时随 `docker compose --profile app up` 一起启动（服务名 `ai-orchestrator`），
配置都在项目根目录 `.env`，见 `docs/06-部署运维/04-AI编排服务.md`。同一个镜像包含本服务和 stub，
用 `AI_ORCHESTRATOR_MODE=api|stub` 切换。

本机直接运行：

```bash
pip install -r scripts/ai-orchestrator/requirements.txt
python3 scripts/ai-orchestrator/server.py
```

默认监听 `8899` 端口，跟 `scripts/ai-orchestrator-stub` 用的是同一个默认端口
（`zhul.ai-task.orchestrator-base-url` 不用改），两个脚本不要同时启动。

启动日志会列出服务商、两类任务的模型和思考强度；服务商或思考强度写错时直接报错退出。

单元测试（假客户端拦截请求，不联网、不产生费用）：在本目录执行 `python -m unittest -v`。

## 会产生真实费用，务必知道这几件事

- 每次"开始AI解析"/"重试"点击，都会真的调用一次 API，按用量计费，不是免费的
- 一条含 10~20 个型号的询盘，每个型号都要联网搜索验证，费用和耗时会明显更高（分钟级），
  这是 design.md 里早就提示过的已知风险，不是这个实现引入的新问题
- API 没有「单次最多花多少钱」的开关，用 `AI_ORCHESTRATOR_MAX_WEB_SEARCHES`（默认 20）
  限制联网搜索次数间接控制；每次调用完成后日志打印 token 用量与搜索次数
- 想省钱先用 `scripts/ai-orchestrator-stub` 跑通页面交互和联调，需要看真实解析
  效果时再切到这个

## 可选环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `AI_ORCHESTRATOR_PROVIDER` | `claude` | 服务商：`claude` 或 `openai` |
| `AI_ORCHESTRATOR_API_KEY` | 无 | 必填，当前服务商的 Key；为空时任务直接回调失败 |
| `AI_STUB_PORT` | `8899` | 监听端口（沿用 stub 的变量名，两边共用同一份 `.env`） |
| `AI_TASK_CALLBACK_TOKEN` | `zhul-erp-ai-callback-secret` | 回调时携带的共享密钥，必须与 zhul-erp-backend 的 `zhul.ai-task.callback-token` 配置一致 |
| `AI_ORCHESTRATOR_PARSE_MODEL` | 见下表 | 询盘解析使用的模型 |
| `AI_ORCHESTRATOR_TRANSLATE_MODEL` | 见下表 | 型号描述翻译、联系人名音译使用的模型 |
| `AI_ORCHESTRATOR_PARSE_EFFORT` | `medium` | 询盘解析的思考强度：`low`、`medium`、`high`、`xhigh`、`max` |
| `AI_ORCHESTRATOR_TRANSLATE_EFFORT` | `medium` | 翻译的思考强度，取值同上 |
| `AI_ORCHESTRATOR_MAX_WEB_SEARCHES` | `20` | 单次解析最多联网搜索次数 |
| `AI_ORCHESTRATOR_TIMEOUT_SECONDS` | `900` | 单次调用的最长等待时间（秒） |

模型留空时使用推荐模型：

| 服务商 | 询盘解析 | 翻译 |
|------|------|------|
| `claude` | `claude-opus-5-5` | `claude-haiku-5-5` |
| `openai` | `gpt-6.1-sol` | `gpt-6-luna` |

填写的模型名原样传给服务商，要与 `AI_ORCHESTRATOR_PROVIDER` 对应。

## 实现要点

- 结构化输出：Claude 用 `output_config.format`、OpenAI 用 `text.format`（严格模式）指定 JSON Schema，
  直接拿到后端要的驼峰字段结构，不用额外写一层格式转换（`confirmed_model`→`confirmedModel`、
  中文置信度→整数这些映射写进了系统提示词，让模型一步到位输出目标格式）。OpenAI 严格模式要求
  字段全部必填，发送前把非必填字段改成可空，收到后删掉值为 null 的字段，回调内容与 Claude 一致
- 联网搜索用服务商托管的 `web_search` 工具，在服务商服务器上执行。Claude 搜索轮次较长时
  返回 `pause_turn`，原样续传继续
- 搜索次数上限：Claude 只统计搜索；OpenAI 的 `max_tool_calls` 把打开网页、页内查找也算在内，
  同样的上限下实际搜索次数可能更少，不够用时调大 `AI_ORCHESTRATOR_MAX_WEB_SEARCHES`
- 图片、PDF 附件由本服务读取后端私有上传目录里的文件，随请求发送；
  Docker 部署时两个容器挂载同一个目录
- Claude 启用服务端 `fallbacks: "default"`：模型因安全策略拒答时由服务端改用推荐的备用模型重试；
  Haiku 没有服务端兜底，用 Haiku 时不带这个参数。OpenAI 没有对应功能，拒答直接回调失败
