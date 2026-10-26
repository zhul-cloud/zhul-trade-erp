# Spec Delta

## MODIFIED Requirements

### Requirement: 内置配置的键名约定
内置配置的键 SHALL 以所属分组代码为第一段，没有例外：如采购分组的 `purchase.transit.default-days`、系统分组的 `sys.site.name`。所有内置配置的键都符合配置键命名规则。`sys.` 开头的键为系统保留，只能是平台内置配置。

#### Scenario: 默认运输天数的键
- **WHEN** 平台管理员在系统参数页的「采购」分组查看默认运输天数
- **THEN** 配置键为 `purchase.transit.default-days`

#### Scenario: 升级后保留已设的天数
- **WHEN** 升级前默认运输天数为 5 天，升级到本版本
- **THEN** `purchase.transit.default-days` 的值仍为 5，发货估算预计到货仍按 5 天；不再存在 `transit.default-days`

#### Scenario: 系统保留键属于系统分组
- **WHEN** 平台管理员在系统参数页查看 `sys.site.name`
- **THEN** 它位于「系统」分组
