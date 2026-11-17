# ai-platform/connection-failover Specification

## Purpose
公司配好的多个 AI 连接方式（本公司 API Key、平台订阅）按顺序自动使用：提交任务时挑出能用的，执行中某个方式出问题就换下一个，失败过的方式暂停一段时间后自动恢复，让 AI 操作不因单个 Key 或订阅出问题而中断。

## Requirements

### Requirement: 连接方式的使用顺序
每个租户的连接方式 SHALL 分为 API 组（本公司 API Key）与订阅组（平台订阅），API 组整体排在订阅组前面；组内顺序由租户在 AI 设置页调整并保存。使用时按「API 组内顺序，再订阅组内顺序」依次尝试，API 组全部不可用才用订阅组。

#### Scenario: API 优先
- **WHEN** 租户配好了 Claude API Key、OpenAI API Key，并获授权使用平台订阅，三者都可用，提交询盘解析
- **THEN** 任务使用 API 组排在第一位的方式，不使用平台订阅

#### Scenario: 调整组内顺序
- **WHEN** 租户把 OpenAI API Key 拖到 Claude API Key 前面并保存，随后提交询盘解析
- **THEN** 任务先用 OpenAI 的 Key

#### Scenario: 订阅不能排到 API 前面
- **WHEN** 租户通过接口提交把某个平台订阅排在 API 方式之前的顺序
- **THEN** 保存被拒绝，原顺序不变

### Requirement: 提交任务时的可用性判断
创建 AI 任务时 SHALL 按顺序挑出当前可用的连接方式交给编排服务。以下方式不可用并跳过：已关闭、API 方式未填 Key、订阅未获授权或平台未登录、处于暂停中。一个可用的都没有时，按「未配置时的处理」与「租户使用平台订阅」的提示规则处理，不创建任务。

#### Scenario: 跳过没有 Key 的方式
- **WHEN** 租户只填了 OpenAI 的 Key，Claude API 排在第一位但没有 Key
- **THEN** 任务使用 OpenAI 的 Key

#### Scenario: 跳过暂停中的方式
- **WHEN** Claude API 在 5 分钟前因鉴权失败进入暂停，租户提交询盘解析
- **THEN** 任务不先尝试 Claude API，直接用下一个可用方式

#### Scenario: 全部不可用
- **WHEN** 租户的 API 方式都没有 Key，平台订阅也都未登录
- **THEN** 用户主动发起的 AI 操作不创建任务并提示 AI 暂不可用

### Requirement: 执行中自动切换
任务执行中某次模型调用因连接方式本身的问题失败时，SHALL 只重做这一次调用，改用列表中的下一个可用方式；同一任务里已成功的调用结果保留，不重复调用。连接方式本身的问题包括：鉴权失败、额度用完、限流或服务商故障且重试后仍失败、调用超时、订阅未登录或达到用量上限。内容本身的错误（返回结果不符合格式、模型拒答、输入不合法）不切换，按原有规则处理。列表用完仍失败时任务失败，失败原因说明各方式都不可用。

#### Scenario: Key 失效后换下一个
- **WHEN** 任务第一次调用时 Claude 的 Key 返回鉴权失败，列表里下一个是 OpenAI API
- **THEN** 这次调用改用 OpenAI 重做，任务成功完成

#### Scenario: 分批解析中途切换
- **WHEN** 大附件询盘分 5 批解析，前 3 批用 Claude API 成功，第 4 批时 Claude 额度用完
- **THEN** 前 3 批结果保留，第 4、5 批改用下一个可用方式，最终结果包含全部 5 批

#### Scenario: 格式错误不切换
- **WHEN** 模型返回的结果不符合要求的格式
- **THEN** 按原有规则处理这次结果，不改用其他连接方式

#### Scenario: 全部失败
- **WHEN** 列表中每个方式都因鉴权失败或服务商故障调用失败
- **THEN** 任务失败，失败原因说明 AI 连接方式都不可用，不含 Key

### Requirement: 失败后暂停与恢复
连接方式因执行中自动切换列出的问题失败后，SHALL 暂停 10 分钟：这段时间内新提交的任务跳过它，到期自动恢复参与。本公司 API Key 的暂停只影响本租户；平台订阅的暂停对所有使用该订阅的租户生效。保存这个方式的设置（如更换 Key）或测试连接成功时，立即解除暂停。

#### Scenario: 暂停期内跳过
- **WHEN** Claude API 因额度用完进入暂停，3 分钟后租户提交翻译任务
- **THEN** 任务不尝试 Claude API

#### Scenario: 到期恢复
- **WHEN** Claude API 暂停已满 10 分钟，租户提交询盘解析
- **THEN** 任务重新先尝试 Claude API

#### Scenario: 换 Key 后立即恢复
- **WHEN** Claude API 处于暂停中，租户更换了新的 Key 并保存
- **THEN** 暂停解除，下一个任务先用 Claude API

#### Scenario: 平台订阅暂停对所有租户生效
- **WHEN** 某租户的任务因 Claude 平台订阅达到用量上限而切换
- **THEN** 10 分钟内其他获授权租户的新任务也跳过 Claude 平台订阅

### Requirement: 切换记录
每个 AI 任务 SHALL 记录实际完成调用所用的连接方式与模型；发生切换时记录每次切换的原连接方式、原因分类与时间，不记录 Key 或服务商返回的原文中可能含有的凭据。AI 设置页 SHALL 在暂停中的连接方式上显示原因与预计恢复时间。

#### Scenario: 查看切换原因
- **WHEN** 任务因 Claude API 鉴权失败切换到 OpenAI API 后完成
- **THEN** 任务记录显示使用 OpenAI API 及其模型，并有一条「Claude API 鉴权失败，已切换」的记录

#### Scenario: 设置页显示暂停
- **WHEN** Claude API 因额度用完进入暂停后，用户打开 AI 设置页
- **THEN** Claude API 卡片显示「暂停中」、原因「额度用完」和恢复时间
