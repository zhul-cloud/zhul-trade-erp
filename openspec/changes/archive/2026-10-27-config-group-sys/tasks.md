# Tasks

## 1. 数据库

- [x] 1.1 新增迁移 `V1.3.16__config_group_sys.sql`：`tenant_id=0`、键在 5 项外观键内、分组为 `appearance` 的行改为 `sys`，更新 `update_time`；新增回退 SQL `sql/build/rollback/V1.3.16__config_group_sys_rollback.sql`。验证：本机库执行后 5 行分组为 `sys`；回退 SQL 在事务中执行恢复为 `appearance` 后回滚

## 2. 代码与测试

- [x] 2.1 前端 `groupLabel.ts`：`appearance` 改为 `sys: '系统'`，注释改为「内置配置的分组 = 键的第一段」；测试同步。验证：Jest、Biome、tsc 通过
- [x] 2.2 `ConfigAppearanceIntegrationTest` 分组断言与过滤改为 `sys`，不合法分组样例改为 `sys`；`ConfigNormalizeContractTest` 内置约定去掉 `sys.` 例外。验证：两个测试类通过；去掉例外后约定测试对全部内置配置成立

## 3. 文档与验证

- [x] 3.1 系统设置 PRD：外观说明、配置键命名规范与配置分组规范中的分组改为 `sys`（系统），去掉 `sys.` 例外的说法。验证：PRD 中不再出现 `appearance` 分组
- [x] 3.2 本机重建后，系统参数页分组页签显示「系统 (5)」、不再有「外观」；`openspec validate config-group-sys --strict` 通过，改动文件为 LF

## Workflow follow-up

- 与本分支一起归档本变更，PR 发往 `release/1.3.0`，说明迁移 V1.3.16 及回退方式。
