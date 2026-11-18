# 验收表：ai-connection-free-order

- 环境：本机 docker（后端、前端为本变更构建，迁移 V1.5.48 已执行），平台租户，BrowserOS neo，2026-11-18
- 自动化：
  - 后端：`AiConfigContractTest`（20，含新增 `subscriptionCanBeOrderedBeforeApi`、`upgradedOrderKeepsApiFirst`）、`AiTaskServiceImplTest`（14）、`BackgroundEntryTenantContractTest`、`TenantIsolationContractTest` 在本机测试库通过。
  - 前端：lint、tsc、antd lint 通过；`ai-settings` Jest 32 个通过。

| # | 验收点 | 方式 | 证据 | 结论 |
| --- | --- | --- | --- | --- |
| A1 | 连接方式是一个列表，没有分组标题与「API Key 都不可用时，才使用平台订阅」 | 页面实测 | AI 设置页四张卡片在同一列表，上方一行说明「按顺序使用；排在前面的出问题时自动改用下一个，改用 API Key 时按该 Key 计费」 | 通过 |
| A2 | 平台订阅可以拖到 API Key 前面，保存条说明新顺序 | 页面实测 + Jest | 拖动 Claude 平台订阅到第 1 位，序号变为 1～4，保存条显示「Claude 平台订阅 排到第 1 位」；Jest 断言保存时提交的顺序 | 通过 |
| A3 | 保存后刷新，顺序保留；顶部状态按新顺序显示 | 页面实测 | 刷新后为 Claude 订阅、Claude API、OpenAI API、OpenAI 订阅；库里 sort_order 依次为 1～4 | 通过 |
| A4 | 任务按列表顺序使用，订阅排在前面时也不必先查登录状态（有可用 API Key 时） | 契约 | `subscriptionCanBeOrderedBeforeApi`：顺序为 subscription:claude、api:openai、api:claude、subscription:openai，查登录状态会让测试失败 | 通过 |
| A5 | 升级后顺序不变（API 在前） | 本机迁移 + 契约 | 本机租户 1001 订阅行由 1、2 变为 21、22，API 行仍为 1、2；`upgradedOrderKeepsApiFirst` 断言 | 通过 |
| A6 | 未授权订阅的公司仍不能保存订阅 | 契约 | `tenantWithoutGrantCannotUseSubscription` 通过 | 通过 |

备注：实测保存在本机平台租户新建的 4 行配置已删除，恢复为实测前的默认状态。
