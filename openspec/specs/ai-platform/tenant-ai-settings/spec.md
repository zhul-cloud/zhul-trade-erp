# ai-platform/tenant-ai-settings Specification

## Purpose
每个租户在系统内维护自己的 AI 编排配置（运行方式、服务商、API Key、模型、思考强度、搜索上限），修改后立即作用于该租户后续的 AI 任务，并能在保存前测试连接；平台租户另可使用部署者个人订阅并查看登录状态。

## Requirements

### Requirement: 租户维护自己的 AI 配置
能访问「业务设置 → AI 设置」菜单的用户 SHALL 能查看本租户的 AI 配置，另有「编辑 AI 设置」按钮权限的用户 SHALL 能修改、测试连接与获取模型列表；配置项为：运行方式（`api` / `subscription`）、服务商（`claude` / `openai`）、API Key、解析模型、翻译模型、解析与翻译的思考强度（`low`、`medium`、`high`、`xhigh`、`max`）、单次解析的最大搜索次数（1–100 的整数）。每个租户一份配置，互不可见；修改后该租户之后提交的 AI 任务立即使用新配置，不需重启任何服务。AI 配置不出现在系统参数的通用列表中。

#### Scenario: 两个租户各用自己的配置
- **WHEN** 租户甲配置服务商 `claude` 与自己的 Key，租户乙配置服务商 `openai` 与自己的 Key，两人各提交一个询盘解析
- **THEN** 甲的任务用甲的 Key 调用 Claude，乙的任务用乙的 Key 调用 OpenAI，互不影响

#### Scenario: 修改立即生效
- **WHEN** 租户把解析模型从留空改为 `claude-sonnet-5-5` 并保存，随后提交询盘解析
- **THEN** 该任务使用 `claude-sonnet-5-5`，编排服务没有重启

#### Scenario: 只能查看的用户
- **WHEN** 用户有 AI 设置菜单、没有「编辑 AI 设置」权限，打开页面后尝试保存
- **THEN** 页面只读显示配置，保存请求被拒绝；没有该菜单的用户连查看也被拒绝

#### Scenario: 非法取值
- **WHEN** 保存时服务商为 `deepseek`、思考强度为 `fast` 或最大搜索次数为 0
- **THEN** 保存失败并提示对应字段的可选值或范围，原配置不变

### Requirement: 留空项使用默认值
服务商、模型与思考强度留空时 SHALL 使用默认值：服务商为 `claude`；解析模型 Claude 为 `claude-opus-5-5`、OpenAI 为 `gpt-6.1-sol`；翻译模型 Claude 为 `claude-haiku-5-5`、OpenAI 为 `gpt-6-luna`；思考强度为 `medium`；最大搜索次数默认 20。界面中两个模型输入框预先填入当前服务商的推荐模型；切换服务商时，没改过的推荐模型随之换成新服务商的推荐模型，用户自己填写的模型保持不变。

#### Scenario: 只填服务商和 Key
- **WHEN** 租户只填写服务商 `openai` 与 Key，其余留空，提交询盘解析
- **THEN** 解析使用 `gpt-6.1-sol`、思考强度 `medium`、最大搜索 20 次

### Requirement: 订阅方式只供平台租户
运行方式 `subscription` SHALL 只能由平台租户（`tenant_id = 0`）选择；其他租户的界面不显示该选项，后端拒绝其他租户保存 `subscription`。订阅方式不需要也不使用 API Key。

#### Scenario: 普通租户提交订阅方式
- **WHEN** 租户 1001 通过接口保存运行方式 `subscription`
- **THEN** 保存失败并提示只能使用 API 方式，原配置不变

#### Scenario: 平台租户使用订阅
- **WHEN** 平台租户选择运行方式 `subscription`、服务商 `openai`，API Key 留空并保存
- **THEN** 保存成功，平台租户之后的 AI 任务通过容器内已登录的 Codex 执行

### Requirement: API Key 加密存储并脱敏展示
API Key SHALL 按 `system/data-encryption` 加密存储；查询接口只返回是否已填写及脱敏值（保留前 7 位与后 4 位，中间固定 16 个点，不随 Key 长度变化，如 `sk-ant-••••••••••••••••abcd`；不足 12 位时整串显示为同样长度的点），任何接口都不返回明文。保存时 Key 留空表示保持原值；填写新 Key 时界面先二次确认再提交。Key 只在提交任务或测试连接时由后端解密，日志与错误信息中不出现明文。

#### Scenario: 查看已保存的配置
- **WHEN** 用户打开 AI 设置页面
- **THEN** API Key 显示为脱敏值，页面源码与接口响应中都没有明文

#### Scenario: 只改模型不改 Key
- **WHEN** 用户只修改翻译模型、Key 留空并保存
- **THEN** 原 Key 保持不变，之后的任务照常使用原 Key

#### Scenario: 更换 Key
- **WHEN** 用户填写新 Key 并点击保存
- **THEN** 先弹出确认；确认后保存，记录修改人与修改时间

### Requirement: 未配置时的处理
租户没有 AI 配置，或 API 方式下没有填写 Key 时，SHALL 视为「AI 未配置」，不使用其他租户或平台的配置：用户主动发起的 AI 操作（询盘解析、型号描述翻译）不创建任务，直接提示「AI 未配置，请在业务设置 → AI 设置 中填写」；业务保存时顺带触发的 AI 任务（保存商机时的联系人名音译）跳过并记录日志，业务保存照常成功。

#### Scenario: 未配置时解析询盘
- **WHEN** 未配置 AI 的租户提交询盘解析
- **THEN** 提示 AI 未配置，不产生 AI 任务，询盘保持提交前的状态

#### Scenario: 未配置时保存商机
- **WHEN** 未配置 AI 的租户保存一个联系人为英文名的商机
- **THEN** 商机保存成功，联系人名不翻译，日志记录跳过原因

#### Scenario: 平台已配置但租户未配置
- **WHEN** 平台租户已填写 Key，租户 1001 未配置，租户 1001 提交询盘解析
- **THEN** 提示 AI 未配置，不使用平台的 Key

### Requirement: 模型下拉列表
解析模型与翻译模型 SHALL 既可手动输入，也可从下拉列表选择；列表在首次展开时获取，使用页面当前填写的 Key（留空时用已保存的 Key），切换服务商后重新获取。API 方式列出该 Key 可用的模型（OpenAI 过滤掉嵌入、语音、图像、审核等非对话模型）；订阅方式的 Codex 列出该 ChatGPT 账号可用的模型并标出默认；订阅方式的 Claude 与没有可用 Key 时只列出默认模型。获取列表不产生调用费用；获取失败时提示原因，仍可手动输入。

#### Scenario: 用 Key 获取 Claude 模型
- **WHEN** 服务商为 `claude`，填写有效 Key 后展开解析模型下拉框
- **THEN** 列出该 Key 可用的 Claude 模型，当前服务商的默认模型标为「默认」

#### Scenario: 手动输入不在列表中的模型
- **WHEN** 用户在解析模型中输入列表里没有的模型名并保存
- **THEN** 保存成功，任务使用该模型名

#### Scenario: 没有 Key
- **WHEN** 服务商为 `openai`，页面与已保存的配置都没有 Key，展开下拉框
- **THEN** 只列出 `gpt-6.1-sol` 与 `gpt-6-luna`，并提示填写 Key 后可获取完整列表

#### Scenario: Codex 订阅
- **WHEN** 平台租户选择订阅方式、服务商 `openai`，容器内 Codex 已登录，展开下拉框
- **THEN** 列出该 ChatGPT 账号可用的模型，并标出账号默认模型

#### Scenario: 获取失败
- **WHEN** Key 无效时展开下拉框
- **THEN** 提示认证失败原因（不含 Key），下拉框只列出默认模型，仍可手动输入

### Requirement: 测试连接
AI 设置页面 SHALL 提供「测试连接」，用页面当前填写的值测试，不需先保存；Key 留空时用已保存的 Key。API 方式下检查 Key 是否有效以及解析模型、翻译模型是否可用，只调用服务商的模型查询接口，不产生调用费用；订阅方式下检查所选服务商的 CLI 是否已登录。结果逐项显示成功或失败原因，失败原因不含 Key。

#### Scenario: Key 有效且模型可用
- **WHEN** 用户填写有效的 Claude Key，模型留空，点击测试连接
- **THEN** 显示 Key 有效、`claude-opus-5-5` 与 `claude-haiku-5-5` 可用

#### Scenario: 模型不存在
- **WHEN** 服务商为 `openai`，解析模型填 `claude-opus-5-5`，点击测试连接
- **THEN** 显示 Key 有效、解析模型不可用及服务商返回的原因

#### Scenario: Key 无效
- **WHEN** 填写错误的 Key 点击测试连接
- **THEN** 显示认证失败，不保存任何内容

### Requirement: 订阅登录状态
平台租户在 AI 设置页面选择订阅方式时 SHALL 显示 Codex 与 Claude 两个 CLI 是否已登录，并给出对应的容器内登录命令；不显示账号邮箱等个人信息。其他租户看不到此区域，对应接口拒绝其他租户访问。

#### Scenario: 平台租户查看
- **WHEN** 平台租户在 AI 设置页面选择订阅方式，容器内 Codex 已登录、Claude 未登录
- **THEN** 显示「Codex 已登录」「Claude 未登录」及 Claude 的登录命令

#### Scenario: 普通租户请求状态接口
- **WHEN** 租户 1001 直接调用订阅状态接口
- **THEN** 请求被拒绝
