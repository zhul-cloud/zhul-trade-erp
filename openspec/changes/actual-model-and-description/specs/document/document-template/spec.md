# Spec Delta

## ADDED Requirements

### Requirement: 客户型号占位符

报价单、文字报价与 PI 模版的明细行 SHALL 支持占位符 `${item.customerModel}`：该行型号与客户型号（询盘确认的型号）忽略大小写、空格与符号后不同时输出客户型号，相同或没有来源询盘时输出空。CI、PL 模版不支持该占位符，上传时按未知占位符拒绝。初始模版不变，需要显示客户型号的租户自行在模版里加上。

#### Scenario: 型号已更正

- **GIVEN** 报价单模版明细行 B 列为 `${item.model}`，C 列（表头「Your Ref.」）为 `${item.customerModel}`
- **WHEN** 导出型号 LC2D18B7C、客户型号 LC2D18B7 的报价单
- **THEN** 该行 B 列输出 LC2D18B7C，C 列输出 LC2D18B7

#### Scenario: 型号没有更正

- **WHEN** 导出型号与客户型号都是 6ES7214-1AG40-0XB0 的行
- **THEN** `${item.customerModel}` 输出为空

#### Scenario: CI 模版不支持

- **WHEN** 上传的 CI 模版 B12 写了 `${item.customerModel}`
- **THEN** 系统拒绝保存并提示「B12：不认识的占位符 ${item.customerModel}」
