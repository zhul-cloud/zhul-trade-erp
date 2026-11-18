# Proposal

## Why

现在 AI 设置把连接方式分成两组：API Key 组固定在前，平台订阅组在后，只有 API 组全部不可用才用订阅。公司希望先用平台订阅（不按量计费），订阅出问题再用付费的 API Key，现有规则做不到。用户 2026-11-18 决定：不再固定 API 优先，所有连接方式在一个列表里拖动排序，按顺序使用。

## What Changes

- **BREAKING**：取消「API 组在前、订阅组在后」的分组规则。所有连接方式（本公司 API Key、平台订阅）排成一个列表，可以任意拖动排序，任务按列表顺序使用，出问题时换下一个（执行中切换、暂停与恢复的规则不变）。
- 后端保存顺序时不再拒绝「订阅排在 API 前面」。`sort_order` 改为整个列表统一编号，不再按组各自编号。
- 迁移：把已有的分组编号换成统一编号，保持各公司当前的实际顺序（API 在前、订阅在后），升级后行为不变，直到公司自己调整。
- 从没保存过的连接方式沿用现在的默认顺序：Claude API、OpenAI API、Claude 订阅、OpenAI 订阅。
- AI 设置页去掉两个分组标题和「API Key 都不可用时，才使用平台订阅」的分隔说明，改成一个可拖动的列表。列表上方一行说明：按顺序使用，排在前面的出问题时自动改用下一个，改用 API Key 时按该 Key 计费。
- 费用提示：订阅排在 API Key 前面时，订阅达到用量上限、未登录或出错，会自动改用后面的付费 API Key。这是公司自己排的顺序，页面说明里写明。
- 未获授权使用平台订阅的公司没有订阅卡片，列表里只有 API Key，与现在相同。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `ai-platform/connection-failover`：「连接方式的使用顺序」改为统一列表顺序，不再分组。
- `ai-platform/tenant-ai-settings`：AI 设置页从两组卡片改为一个列表；平台订阅的授权与使用规则里「订阅组排在 API 组之后」的表述随之修改。

## Impact

- 后端：
  - `AiConfigServiceImpl`：去掉保存时的分组校验，排序与编号按整个列表统一；`chain()` 的订阅登录状态检查逻辑不变（只在没有可用 API Key 时查询）。
  - `SaveAiSettingsRequest`、`AiConfigVO` 注释。
  - 迁移 `V1.5.48`：重新编号 `sys_ai_provider_config.sort_order`。
  - 契约测试 `AiConfigContractTest` 相关用例。
- 前端：`pages/system/ai-settings/index.tsx`（两个拖动区域合成一个，去掉分组说明）及测试。
- 编排服务：不改，仍按后端给的顺序使用。
- 原型：`docs/03-产品原型/00-用户域/07-AI设置/AI设置.op` 新增 V5-S1、V5-S2。
