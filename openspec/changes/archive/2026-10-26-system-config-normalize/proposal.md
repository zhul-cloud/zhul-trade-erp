# Proposal

## Why

系统参数的命名和类型规则一直没有明确，代码、数据库和 PRD 各写各的：
- 内置配置的键用「点分段 + 段内短横线」（如 `quotation.hint.to-confirm`），但新增配置的校验（前端、后端都是 `^[a-z][a-z0-9_.]{3,127}$`）不允许短横线，只允许下划线，用户照着内置配置的样子起名会被拒绝；
- 运输天数的键 `transit.default-days` 第一段不是它的分组 `purchase`，和其他内置配置「第一段 = 分组」的规律不一致；
- 有两项内置配置的类型是 `NUMBER`（`transit.default-days`、`finance.bu.min-share-percent`），但前后端都不认这个类型：系统参数页按普通文本编辑，服务端也不校验，可以存进「abc」，而代码实际按整数读取；
- 系统设置 PRD 的「内置配置项参考清单」列了 11 项从未实现的 `sys.security.*`、`sys.register.*`、`sys.email.*`、`sys.watermark.*`，却漏掉了实际存在的 14 项业务配置。

外观（`site-appearance`）已统一了配置分组，这次把键名、类型和 PRD 清单一起理顺。

## What Changes

- **配置键命名规范**：键由 2 段及以上小写段组成，段之间用点，段内多个单词用短横线（如 `purchase.transit.default-days`）；只允许小写字母、数字、点和短横线，4 – 128 个字符。内置配置的第一段为所属分组代码，`sys.` 开头的为系统保留键。新增自定义配置的前后端校验统一按此规则，**不再允许下划线**（生产与本机都没有含下划线的键）。
- **运输天数改名**：`transit.default-days` 改为 `purchase.transit.default-days`，新增迁移改写所有租户的这一行，已设的天数保持不变；业务设置里修改默认运输天数的功能不变。
- **`NUMBER` 统一为 `INTEGER`**：两项 `NUMBER` 配置改为 `INTEGER`（实际都是整数），修改时按整数校验、用数字输入框编辑；业务设置与事业部设置第一次修改时生成的租户副本也写 `INTEGER`。服务端修改配置值时，不认识的类型直接拒绝，不再当作文本放过。
- **PRD 与实际一致**：系统设置 PRD 的内置清单改为实际存在的 19 项，删除 11 项未实现的配置；新增配置的键规则说明同步。

## Capabilities

### New Capabilities

- `system/system-config`：系统参数的配置键命名规则、配置类型及其取值校验、内置配置的键名约定。

### Modified Capabilities

（无。`system/transit-time` 与 `system/business-unit` 的业务行为不变，只是所读配置的键名与类型调整。）

## Impact

- **数据库**：新增迁移 `V1.3.15`，改名运输天数键、把 `NUMBER` 改为 `INTEGER`，附回退 SQL。生产只有这两行平台级配置，没有租户副本，也没有自定义配置。
- **后端**：`ConfigServiceImpl` 值校验、`SaveConfigRequest` 键校验、`TransitTimeService` 常量与副本类型、`BusinessUnitService` 副本类型，相关测试。
- **前端**：系统参数页新增配置的键校验与提示。
- **文档**：系统设置 PRD。
- 没有新增 `.env` 配置项，接口路径和字段不变。
