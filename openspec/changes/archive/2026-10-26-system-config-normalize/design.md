# Design

## Context

- 配置存在 `sys_config` 中，`tenant_id=0` 为平台配置，租户第一次修改时生成自己的副本（同键、`tenant_id` 为租户）。
- 本机与生产的内置配置共 19 项：
  - 5 项 `sys.*` 外观配置；
  - 14 项业务配置，键都是「分组.…」形式，只有 `transit.default-days` 例外；
  - 没有含下划线的键。
- 生产只有平台级配置：两项 `NUMBER` 配置都没有租户副本，也没有任何自定义配置（2026-10-26 只读查询）。
- 两项 `NUMBER` 配置的读取都用 `Integer.parseInt`：
  - 运输天数解析失败或 ≤ 0 时回退 3 天；
  - 最低分摊比例解析失败时回退默认值。
  - 专门的设置入口（业务设置、事业部设置）会校验范围，但系统参数页的通用编辑绕过了这些检查，对 `NUMBER` 也完全不校验。

## Goals / Non-Goals

**Goals:**
- 一条命名规则同时约束内置配置和自定义配置，前后端校验一致。
- 存进去的值都能按类型正确读取。
- PRD 只写系统实际有的配置。

**Non-Goals:**
- 不给系统参数页的通用编辑加业务范围校验，例如运输天数 1 – 30、分摊比例 × 事业部数 ≤ 100%。这些范围由专门的设置入口负责。系统参数页改出超范围的值时，现有读取逻辑会回退默认值或按值计算，见风险。
- 不新增小数类型：目前没有需要小数的配置，以后需要时再加 `DECIMAL`。
- 不实现 PRD 中删除的安全、注册、邮件、水印配置。

## Decisions

### 1. 命名规则：点分段，段内短横线，不允许下划线

正则为 `^[a-z][a-z0-9]*(-[a-z0-9]+)*(\.[a-z][a-z0-9]*(-[a-z0-9]+)*)+$`，长度另校验 4 – 128 个字符。前端 `pages/system/config/index.tsx` 和后端 `SaveConfigRequest` 用同一个正则。

- 选短横线而不是下划线，是因为现有 14 项业务配置全部用短横线，生产没有含下划线的键，改规则不影响任何数据。
- 备选方案是两者都允许。这样等于没有规范，同一含义可能出现 `auto-close` 和 `auto_close` 两个键，所以不采用。

### 2. 运输天数改名，用迁移改写所有租户的行

新增迁移 `V1.3.15__system_config_normalize.sql`：

```sql
UPDATE sys_config SET config_key = 'purchase.transit.default-days', update_time = NOW()
 WHERE config_key = 'transit.default-days';
```

迁移不限租户，平台配置和可能存在的租户副本一起改。代码常量 `TransitTimeService.CONFIG_DEFAULT_DAYS` 同步修改。

- 迁移和代码随同一版本上线：Flyway 在应用启动时先执行迁移，新代码读到的已经是新键名，不存在新旧键并存的窗口。
- 备选方案是代码同时读新旧两个键。这会多一层兼容代码，而且上线顺序本来就能保证一致，所以不采用。

### 3. `NUMBER` 改为 `INTEGER`；未知类型拒绝

同一迁移中执行：

```sql
UPDATE sys_config SET config_type = 'INTEGER', update_time = NOW()
 WHERE config_type = 'NUMBER';
```

`TransitTimeService`、`BusinessUnitService` 生成租户副本时改为写 `INTEGER`。

`ConfigServiceImpl.validateValueByType` 的处理调整：
- 原来 `STRING` 和 `default` 共用一个分支；
- 改为 `STRING` 单独一个分支，`default` 抛出「配置类型不支持」；
- 类型为空时也拒绝。目前所有配置都有类型。

前端编辑控件已按 `INTEGER` 显示数字输入框，不需要改。

备选方案是新增 `NUMBER` 或 `DECIMAL` 类型。现有两项实际都是整数，读取代码也按整数处理，新增类型只是多一套前后端分支，所以不采用。

### 4. 内置键名约定用测试守住

新增集成测试：读取 `tenant_id=0` 的全部内置配置，断言以下三点：
- 键符合命名规则；
- 非 `sys.` 开头的键，第一段等于 `config_group`；
- 类型属于 5 种之一。

以后有人新增内置配置违反约定时，测试会失败。

## Risks / Trade-offs

- [外部脚本或文档仍引用旧键 `transit.default-days`] → 已用 `git grep` 核对，只有 `TransitTimeService`、迁移 `V1.2.39` 和 `ReceivingContractTest` 引用。`V1.2.39` 是已执行的迁移，不改；测试同步修改。
- [迁移在租户副本已存在新键时冲突] → 现在不会出现：新键此前不存在，生产没有租户副本。迁移仍按「旧键改名」写，不做合并。
- [系统参数页仍能把运输天数改成 0 或 100] → 0 会回退 3 天，100 会按 100 天估算。这是现有行为，本变更不处理，见 Non-Goals。
- [已有类型为空或拼错的自定义配置在修改时被拒] → 生产没有自定义配置，本机没有类型为空的数据。

## Migration Plan

1. 上线时 Flyway 执行 `V1.3.15`（改名 + 改类型），然后新代码启动。
2. 需要回退时：执行 `sql/build/rollback/V1.3.15__system_config_normalize_rollback.sql`，把键改回 `transit.default-days`、两项类型改回 `NUMBER`，删除 `flyway_schema_history` 中 1.3.15 的记录，然后部署上一版代码。
3. 上线后平台管理员在系统参数页「采购」分组确认键名为 `purchase.transit.default-days`、值不变。
