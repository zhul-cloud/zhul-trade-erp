# Proposal

## Why

系统名称、Logo 等 5 项平台配置的键都是 `sys.` 开头，分组却叫「外观」（代码 `appearance`）。这样「内置配置的键以分组代码为第一段」只能给 `sys.` 开一个例外；而且「外观」只是这 5 项现在的用途，以后平台级的其他系统配置也会用 `sys.` 开头，放不进「外观」分组。用户确认改为「系统」。

## What Changes

- 这 5 项配置的分组从 `appearance`（外观）改为 `sys`（系统）。新增迁移 `V1.3.16` 改写分组，不改 `V1.3.14`（已合并到 `release/1.3.0`，别人本机可能已执行）。
- 内置配置键名约定去掉 `sys.` 的例外：所有内置配置的键第一段都等于分组代码。
- 功能名称「外观」不变（外观配置、提示「外观由平台统一管理」），只是分组归到「系统」。
- 前端分组对照表改为 `sys` → 「系统」，删除 `appearance`；测试、PRD 同步。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `system/site-appearance`：外观配置所属分组改为「系统」（`sys`）；分组显示名清单同步。
- `system/system-config`：内置配置键名约定去掉 `sys.` 例外。

## Impact

- 数据库：迁移 `V1.3.16`（5 行平台配置的 `config_group`），附回退 SQL；没有租户副本（外观配置租户不可改）。
- 后端：仅测试（`ConfigAppearanceIntegrationTest`、`ConfigNormalizeContractTest`）。
- 前端：`pages/system/config/groupLabel.ts` 及测试。
- 文档：系统设置 PRD。没有 `.env` 变更，接口不变。
