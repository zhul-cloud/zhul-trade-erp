## REMOVED Requirements

### Requirement: 连接方式的使用顺序
**Reason**: 不再把连接方式分成 API 组与订阅组、固定 API 优先；改为一个列表任意排序（用户 2026-11-18 决定）。
**Migration**: 由「连接方式按列表顺序使用」取代；升级时保持各公司当前的实际顺序（API Key 在前、平台订阅在后），公司自己调整后按新顺序使用。

## ADDED Requirements

### Requirement: 连接方式按列表顺序使用
每个租户的连接方式（本公司 API Key、获授权时的平台订阅）SHALL 排成一个列表，顺序由租户在 AI 设置页任意调整并保存，API Key 与平台订阅可以互相穿插。使用时按列表顺序依次尝试，跳过不可用的方式。从没保存过顺序的租户按默认顺序使用：Claude API Key、OpenAI API Key、Claude 平台订阅、OpenAI 平台订阅。

#### Scenario: 默认 API 在前
- **WHEN** 租户配好了 Claude API Key、OpenAI API Key，并获授权使用平台订阅，从没调整过顺序，四者都可用，提交询盘解析
- **THEN** 任务先用 Claude API Key

#### Scenario: 订阅排在第一位
- **WHEN** 租户把 Claude 平台订阅拖到第一位并保存，Claude 订阅可用，提交询盘解析
- **THEN** 任务使用 Claude 平台订阅，不使用 API Key

#### Scenario: 订阅出问题改用后面的 API Key
- **WHEN** 列表顺序为 Claude 平台订阅、OpenAI API Key，任务调用时 Claude 订阅达到用量上限
- **THEN** 这次调用改用 OpenAI API Key 重做（按该 Key 计费），Claude 订阅进入暂停

#### Scenario: 升级后顺序不变
- **WHEN** 升级前租户的顺序为 OpenAI API Key、Claude API Key，订阅组为 Claude、OpenAI
- **THEN** 升级后列表为 OpenAI API Key、Claude API Key、Claude 平台订阅、OpenAI 平台订阅，任务使用顺序与升级前相同
