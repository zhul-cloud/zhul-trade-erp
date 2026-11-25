# AI-016 订阅用量上限的提示不准确

## 结论 / 下一步

未修复，仅登记。2026-11-24 生产询盘 AI 解析失败，实际原因是 ChatGPT 订阅每周额度用完（Codex 返回 usageLimitExceeded，提示 Nov 25th 1:59 PM 恢复），用户界面只显示「订阅 CLI 调用失败；请检查登录、订阅额度、模型与容器运行环境」。用户当天额度已恢复，暂不修。下一步：需要时让 worker 识别 Codex 的 usage_limit_exceeded（及 Claude 对应错误），回传「订阅已达到用量上限，预计某时恢复」；server.py failover_reason 不再把所有 runtime 归为 usage_limit。

## 范围与负责人

| 字段 | 内容 |
| --- | --- |
| 负责人 / 协作者 | 烛龙开源 · Claude Code |
| 关联 ID / 原专题 | AI-016 |
| 目标与影响范围 | scripts/ai-orchestrator/subscription.py（run_codex 只回传 runtime 错误码）、server.py（failover_reason 把 runtime 一律记为 usage_limit） |
| 起点 commit / 工作分支 | develop f0b44be2；未开工 |
| OpenSpec | 未确定 |
| PR / 目标分支 | 未创建 |
| 合并证据 | 未合并 |
| 目标版本 / 环境 | 未定 |

## 实施与验证结果

| 命令 / 操作与证据 | 环境、commit / 版本 | 预期 | 实际 | 结论 |
| --- | --- | --- | --- | --- |
| 生产容器内用平台订阅模型 gpt-6.1-sol 发极小请求，打印 turn 错误 | 生产 v1.47.2 之前 | 查明真实原因 | usageLimitExceeded，Nov 25th 1:59 PM 恢复 | 根因确认 |

## 合并、部署与逐 ID 验收

| ID | 修复 / 验收范围 | 部署与目标环境验收证据 | 验收人 / 日期 | 结论与剩余项 |
| --- | --- | --- | --- | --- |
| AI-016 | 订阅用量上限提示 | 未执行 | 未验收 | 待修 |

## 阻塞与下一步

等用户安排。
