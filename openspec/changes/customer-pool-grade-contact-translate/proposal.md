# Proposal

## Why

用户 2026-11-25 反馈两处客户管理的不便：公海客户看不到经营等级、潜力等级，没法挑值得认领的客户（规范已要求显示，页面没做到）；主联系人中文名只能保存后等后台翻译，不像客户名称那样能当场点「AI 翻译」看结果。

## What Changes

- 公海客户列表显示经营等级、潜力评级两列（补齐规范已有要求），并可按这两列排序；取消排序回到默认的放入时间倒序。
- 公海客户接口增加排序参数，口径与客户列表的等级排序一致。
- 新建 / 编辑客户的主联系人中文名、联系人卡片新增 / 编辑联系人的中文名旁增加「AI 翻译」按钮：按英文姓名当场翻译，译名填进输入框，由人确认后随表单保存；保存后的自动翻译规则不变。
- 后端增加联系人姓名的预览翻译接口（提交、查询结果），只返回译名不写库，复用现有的联系人姓名翻译技能。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `master-data/customer-pool`：公海客户列表可按经营等级、潜力评级排序。
- `master-data/customer-profile`：联系人中文名增加「AI 翻译」按钮（当场翻译，人确认后保存）。

## Impact

- 后端：`CustomerPoolQuery` 加排序字段，`CustomerServiceImpl.poolPage` 支持排序；`ContactNameTranslations` 加预览与查询，`CustomerController` 加两个接口。
- 前端：`customer/pool.tsx` 加两列与排序；`customer/form.tsx`、`customer/profile/ContactsCard.tsx` 中文名旁加按钮；`customer/nameTranslation.ts` 支持联系人姓名接口。
- 无数据库迁移，无新配置项。
- 与 `feat/ui-redesign` 分支同样改动 `customer/pool.tsx`（列设置），合并时需处理冲突。
