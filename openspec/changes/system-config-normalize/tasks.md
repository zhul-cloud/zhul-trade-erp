# Tasks

## 1. 数据库迁移

- [x] 1.1 新增迁移 `V1.3.15__system_config_normalize.sql`：把 `transit.default-days` 改名为 `purchase.transit.default-days`（所有租户），把 `config_type='NUMBER'` 改为 `INTEGER`，同时更新 `update_time`。验证：本机库执行后，查询确认旧键不存在、新键值不变、没有 `NUMBER` 类型的行
- [x] 1.2 新增回退 SQL `sql/build/rollback/V1.3.15__system_config_normalize_rollback.sql`：键改回旧名，类型只对 `purchase.transit.default-days` 和 `finance.bu.min-share-percent` 两个键改回 `NUMBER`（不动其他 `INTEGER` 配置），注释写明还要删除 flyway 记录。验证：在本机库的事务中执行回退 SQL，确认恢复原状后回滚事务

## 2. 后端

- [x] 2.1 `TransitTimeService.CONFIG_DEFAULT_DAYS` 改为 `purchase.transit.default-days`，生成租户副本时类型改写 `INTEGER`；同步修改 `ReceivingContractTest` 中的旧键。验证：现有快递时效、收货相关测试通过
- [x] 2.2 `BusinessUnitService` 生成最低分摊比例租户副本时类型改写 `INTEGER`。验证：现有事业部测试通过
- [x] 2.3 `SaveConfigRequest.configKey` 改用新的命名规则正则，提示语按规范更新。验证：集成测试覆盖 `custom.order.auto-close` 能保存，`auto_close`、单段、空段、以短横线开头的段、大写均被拒
- [x] 2.4 `ConfigServiceImpl.validateValueByType`：`STRING` 单独一个分支，未知类型或类型为空时抛出「配置类型不支持」。验证：集成测试覆盖 `purchase.transit.default-days` 改为「abc」被拒并提示「配置值必须为整数」，以及不支持的类型被拒
- [x] 2.5 新增内置配置约定测试：读取 `tenant_id=0` 的内置配置，断言键符合命名规则；非 `sys.` 键的第一段等于分组代码；类型属于 5 种之一。验证：测试通过；临时把某个键改坏时测试失败（只在本地确认，不提交）

## 3. 前端

- [x] 3.1 系统参数页新增配置的键校验正则与提示语和后端一致，输入提示示例改为 `custom.order.auto-close`。验证：Jest 覆盖正则的合法与非法样例，Biome、tsc 通过
- [x] 3.2 本机 Docker 重建后，在系统参数页确认：「采购」分组显示 `purchase.transit.default-days`，值不变；两项原 `NUMBER` 配置显示为 INTEGER，用数字输入框编辑；新增 `custom.order.auto-close` 能保存，`custom.order.auto_close` 被拒。在业务设置修改默认运输天数，确认估算仍正确

## 4. 文档

- [x] 4.1 系统设置 PRD：内置配置清单改为实际 19 项（保留 5 项外观，补 14 项业务配置，删除 11 项 `sys.security.*`、`sys.register.*`、`sys.email.*`、`sys.watermark.*`）；配置键字段说明与新增弹窗的键校验规则按命名规则更新；类型说明写明只有五种类型。验证：对照本机库 `tenant_id=0` 的内置配置逐项核对
- [x] 4.2 运行 `openspec validate system-config-normalize --strict`，检查改动文件都是 LF

## Workflow follow-up

- PR 发往 `release/1.3.0`，说明里写明迁移版本 V1.3.15 及回退方式，没有新增 `.env` 项。
- 上线后平台管理员在系统参数「采购」分组确认运输天数的键名和值；之后归档本变更。
