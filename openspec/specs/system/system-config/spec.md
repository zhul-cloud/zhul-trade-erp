# system/system-config Specification

## Purpose
约定系统参数的配置键命名规则、配置类型及其取值校验，以及内置配置的键名约定，让内置配置与用户新增的自定义配置遵循同一套规则，存进去的值都能被正确读取。

## Requirements

### Requirement: 配置键命名规则
配置键 SHALL 由 2 段及以上组成，段之间用点分隔；每段以小写字母开头，只含小写字母和数字，段内多个单词用短横线连接；整个键 4 – 128 个字符。新增自定义配置时，前端与服务端都按此规则校验，不符合时提示「配置键由小写字母、数字和短横线组成的多段名称，段之间用点分隔，如 custom.order.auto-close」；`sys.` 开头的键为系统保留，不能新增。

#### Scenario: 合法的键
- **WHEN** 管理员新增自定义配置，键填 `custom.order.auto-close`
- **THEN** 配置保存成功

#### Scenario: 键里有下划线
- **WHEN** 管理员新增自定义配置，键填 `custom.order.auto_close`
- **THEN** 系统拒绝并给出命名规则提示，配置不保存

#### Scenario: 键只有一段或格式不对
- **WHEN** 新增自定义配置的键为 `autoclose`、`custom..close`、`custom.-close` 或 `Custom.close`
- **THEN** 系统拒绝并给出命名规则提示

#### Scenario: 绕过页面直接调用接口
- **WHEN** 通过接口新增配置，键为 `custom.order.auto_close`
- **THEN** 服务端同样拒绝

### Requirement: 内置配置的键名约定
内置配置的键 SHALL 以所属分组代码为第一段（如采购分组的 `purchase.transit.default-days`）；`sys.` 开头的系统保留键除外，按用途归入分组（如 `sys.site.name` 属于外观分组）。所有内置配置的键都符合配置键命名规则。

#### Scenario: 默认运输天数的键
- **WHEN** 平台管理员在系统参数页的「采购」分组查看默认运输天数
- **THEN** 配置键为 `purchase.transit.default-days`

#### Scenario: 升级后保留已设的天数
- **WHEN** 升级前默认运输天数为 5 天，升级到本版本
- **THEN** `purchase.transit.default-days` 的值仍为 5，发货估算预计到货仍按 5 天；不再存在 `transit.default-days`

### Requirement: 配置类型与取值校验
配置类型 SHALL 只有字符串 `STRING`、整数 `INTEGER`、布尔 `BOOLEAN`、JSON `JSON`、网址 `URL` 五种。修改配置值时，服务端按类型校验：整数必须是整数，布尔只能是 true / false，JSON 必须格式正确，网址必须以 `http://`、`https://` 或 `/` 开头，字符串最长 4096 个字符；类型不在这五种之内时拒绝修改并提示「配置类型不支持」。系统参数页按类型显示对应的编辑控件（整数用数字输入框）。

#### Scenario: 默认运输天数填非数字
- **WHEN** 平台管理员在系统参数页把默认运输天数改为「abc」
- **THEN** 系统拒绝并提示「配置值必须为整数」，原值不变

#### Scenario: 事业部最低分摊比例按整数编辑
- **WHEN** 平台管理员编辑「事业部最低分摊比例（%）」
- **THEN** 编辑框为数字输入框，配置类型显示为 INTEGER

#### Scenario: 租户第一次修改生成副本
- **WHEN** 租户在业务设置中第一次修改默认运输天数，或在事业部设置中第一次修改最低分摊比例
- **THEN** 生成的租户配置类型为 INTEGER，键与平台配置相同

#### Scenario: 不支持的类型
- **WHEN** 某条配置的类型不是这五种之一，有人修改它的值
- **THEN** 系统拒绝并提示「配置类型不支持」
