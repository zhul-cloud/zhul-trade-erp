# AI 编排服务（真实实现，基于 Claude Code）

跟 `scripts/ai-orchestrator-stub/` 不是一回事——那个是免费的假数据占位脚本，
这个是真的会调用 AI、产生真实费用的实现，用的是本机已登录的 `claude` CLI
（Claude Code 订阅账号即可，不需要单独申请 ANTHROPIC_API_KEY）。

对应 `openspec/changes/add-inquiry-management/design.md` 决策3的通用契约，跟
zhul-erp-backend 之间走同一套 webhook 接口，两边代码都不用改，直接把这个服务
指向和 stub 一样的端口/回调地址即可切换。

## 前置条件

1. 本机装了 Claude Code 并且已登录（`claude` 命令能跑，随便跑个 `claude -p "hi"` 试一下）
2. `skills/` 目录下的 `inquiry-parser/SKILL.md`、`order-splitter/SKILL.md`
   是从团队现有的"烛龙询盘助手"复制过来的，改动请求先跟维护这套 skill 的人对齐

## 运行

```bash
python3 scripts/ai-orchestrator/server.py
```

默认监听 `8899` 端口，跟 `scripts/ai-orchestrator-stub` 用的是同一个默认端口
（`zhul.ai-task.orchestrator-base-url` 不用改），两个脚本不要同时启动。

## 会产生真实费用，务必知道这几件事

- 每次"开始AI解析"/"重试"点击，都会真的调用一次 `claude -p`，从你当前登录账号
  的用量里扣钱，不是免费的
- 实测费用：处理2个型号（含联网搜索验证）大约 $0.28～$0.34，耗时 45～70 秒；
  一条含 10~20 个型号的询盘，费用和耗时会明显更高（分钟级、数美元级都有可能），
  这是 design.md 里早就提示过的已知风险，不是这个实现引入的新问题
- 用 `AI_ORCHESTRATOR_MAX_BUDGET_USD`（默认 `5.00`）给单次调用设费用上限，
  超过就会失败并回调"失败"状态，不会无限烧钱，但没法完全避免产生费用
- 想省钱先用 `scripts/ai-orchestrator-stub` 跑通页面交互和联调，需要看真实解析
  效果时再切到这个

## 可选环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `AI_STUB_PORT` | `8899` | 监听端口（沿用 stub 的变量名，方便两边共用同一份 `.env`） |
| `AI_TASK_CALLBACK_TOKEN` | `zhul-erp-ai-callback-secret` | 回调时携带的共享密钥，必须与 zhul-erp-backend 的 `zhul.ai-task.callback-token` 配置一致 |
| `CLAUDE_BIN` | `claude` | `claude` 可执行文件路径，一般不用改 |
| `AI_ORCHESTRATOR_MAX_BUDGET_USD` | `5.00` | 单次调用的费用上限 |
| `AI_ORCHESTRATOR_CLAUDE_TIMEOUT_SECONDS` | `900` | 单次调用的最长等待时间（秒） |

## 已验证过的技术选型（写代码前实测过）

- 用 `--json-schema` 让 Claude 直接按我们后端要的驼峰字段结构输出，不用额外
  写一层格式转换（`confirmed_model`→`confirmedModel`、中文置信度→整数这些映射
  直接写进了系统提示词里，让模型一步到位输出目标格式）
- `--json-schema` 模式**不会**阻止真实的联网搜索——实测搜索请求数记在
  `modelUsage.<model>.webSearchRequests` 里（Claude Code 内部搜索这类子任务
  会路由到一个更便宜的模型执行），不在顶层 `usage.server_tool_use` 字段里，
  一开始差点因为看错字段误以为没有真的搜索
- `--allowedTools WebSearch` + `--permission-prompts none`：只精确放行联网
  搜索，其余工具自动拒绝而不是卡住等交互确认，不需要 `--dangerously-skip-permissions`
