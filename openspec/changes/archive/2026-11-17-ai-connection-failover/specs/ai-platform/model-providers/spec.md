# Spec Delta

## MODIFIED Requirements

### Requirement: 按配置选择模型服务商
AI 编排服务 SHALL 按每个任务携带的租户连接方式列表选择模型服务商：依次使用列表中的连接方式，每个连接方式自带服务商（`claude` / `openai`，留空时为 `claude`）、用法与模型设置；遇到连接方式本身的问题时按 `ai-platform/connection-failover` 换下一个。服务商适用于 API 方式和订阅方式；部署环境变量 `AI_ORCHESTRATOR_MODE=stub` 时为假数据服务，不调用任何服务商，忽略任务携带的配置。同一个编排服务实例可以同时为不同租户使用不同服务商。

#### Scenario: 未配置服务商时使用 Claude
- **WHEN** 后端提交一个询盘解析任务，携带的第一个连接方式为 API 方式、服务商留空
- **THEN** 编排服务调用 Claude 完成解析，并按原有契约回调结果

#### Scenario: 切换到 OpenAI
- **WHEN** 租户把 OpenAI API Key 排到第一位并填写 Key，随后提交询盘解析
- **THEN** 编排服务调用 OpenAI 完成解析，回调结果的结构与使用 Claude 时一致，编排服务无需重启

#### Scenario: 每个连接方式用自己的模型
- **WHEN** 列表第一位是 Claude API（解析模型 `claude-opus-5-5`），第二位是 OpenAI API（解析模型 `gpt-6.1-sol`），Claude 调用鉴权失败
- **THEN** 改用 OpenAI 重做时使用 `gpt-6.1-sol` 与 OpenAI 卡片上的思考强度、搜索次数

#### Scenario: 假数据模式不受服务商影响
- **WHEN** `AI_ORCHESTRATOR_MODE=stub`，任务携带的服务商为 `openai` 且 Key 为空
- **THEN** 编排服务照常返回固定假数据，不调用任何服务商，也不因缺少 Key 报错
