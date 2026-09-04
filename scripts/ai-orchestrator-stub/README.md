# AI 编排服务本地 stub

不是真正的 AI 编排服务实现——`openspec/changes/add-inquiry-management/design.md` 明确该服务本次不在实现范围内。这个脚本只是为了让 `zhul-erp-backend` 能在本地跑通"提交询盘解析任务 → 异步 webhook 回调"这条链路，不需要等真正的 AI 编排服务落地。

## 运行

```bash
python3 scripts/ai-orchestrator-stub/server.py
```

默认监听 `8899` 端口，与 `zhul-erp-backend` 的默认配置 `zhul.ai-task.orchestrator-base-url=http://localhost:8899` 一致，无需改动即可联调。

## 行为

1. 收到 `POST /skills/inquiry-parse-and-split/run` 后，立即返回 `202 {"job_id": "..."}`。
2. 等待若干秒（默认3秒）后，向请求体里的 `callback_url` 发起回调：`POST {callback_url}`，携带请求头 `X-AI-Callback-Token`，body 为固定的示例拆单结果（Siemens PLC + Schneider 变频器 + 未识别品牌传感器三组，对应 UI 原型"客户询盘详情-待确认拆单预览"页展示的数据）。

## 可选环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `AI_STUB_PORT` | `8899` | 监听端口 |
| `AI_TASK_CALLBACK_TOKEN` | `zhul-erp-ai-callback-secret` | 回调时携带的共享密钥，必须与 `zhul-erp-backend` 的 `zhul.ai-task.callback-token` 配置一致 |
| `AI_STUB_DELAY_SECONDS` | `3` | 受理后延迟多久再回调，模拟真实解析耗时 |

只依赖 Python 标准库，无需 `pip install` 任何依赖。
