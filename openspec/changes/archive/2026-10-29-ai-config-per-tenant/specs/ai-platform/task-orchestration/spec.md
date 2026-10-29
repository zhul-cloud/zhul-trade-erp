## MODIFIED Requirements

### Requirement: 提交任务给 AI 编排服务
系统 SHALL 以统一契约向 AI 编排服务提交任务：请求携带 skill 标识、输入数据、回调地址，以及发起任务时当前租户的 AI 配置（Key 为解密后的值，只出现在请求体中，不写入 AI 任务记录和日志）；AI 编排服务同步返回受理确认（202 状态码及任务标识），实际处理结果通过异步回调返回，Java 侧不同步等待处理完成。

#### Scenario: 提交后不阻塞等待
- **WHEN** Java 后端向 AI 编排服务提交一个任务请求
- **THEN** AI 编排服务在受理后立即返回确认，Java 后端不阻塞等待处理结果

#### Scenario: 任务携带发起时的配置
- **WHEN** 租户提交任务后、编排服务处理完成前修改了 AI 配置
- **THEN** 已提交的任务仍按提交时携带的配置处理，之后提交的任务使用新配置

#### Scenario: Key 不落库
- **WHEN** 任务提交成功后查看 AI 任务记录与后端、编排服务日志
- **THEN** 都不包含 API Key 明文

## ADDED Requirements

### Requirement: 编排服务校验共享令牌
AI 编排服务的所有接口（提交任务、测试连接、查询订阅登录状态）SHALL 校验请求头 `X-AI-Callback-Token` 与部署配置 `AI_TASK_CALLBACK_TOKEN` 一致；不一致或缺失时返回 401，不受理任务、不执行测试、不返回状态。

#### Scenario: 令牌错误
- **WHEN** 请求携带错误的令牌提交任务
- **THEN** 编排服务返回 401，不处理任务，也不回调

#### Scenario: 后端正常提交
- **WHEN** 后端按部署配置的令牌提交任务
- **THEN** 编排服务返回 202 并正常处理
