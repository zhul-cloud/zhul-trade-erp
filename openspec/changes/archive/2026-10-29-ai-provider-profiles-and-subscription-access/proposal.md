# Proposal

## Why

当前每个租户只有一组 AI Key 与模型：从 Claude 切到 OpenAI 会覆盖原来的 Key 和模型，切回去要重新填写。平台已登录的订阅只能平台自己用，无法有控制地开放给其他公司。

## What Changes

- 每个服务商的「本公司 API Key」设置各自保存（Key、模型、思考强度、搜索次数），切换不再互相覆盖；当前使用哪一种单独记录。
- 平台订阅可按套餐授权给公司：新增按钮资源「使用平台订阅」（`system:ai-config:use-subscription`），平台在套餐中勾选即开放、取消即收回。获授权的公司使用平台为各服务商设置的订阅模型，只读。
- 订阅状态：平台看到已登录 / 未登录与登录命令；租户只看到可用 / 不可用，不暴露平台账号信息。租户只能选可用的订阅；创建 AI 任务前再查授权与可用性，不可用时提示 AI 暂不可用，不自动切换服务商或改用付费 API。
- **BREAKING** AI 配置接口改为「当前选择 + 各连接方式设置」的结构，前后端同批上线。
- 页面按已确认的 OpenPencil 原型 V3（连接方式列表，`docs/03-产品原型/00-用户域/07-AI设置/AI设置.op` 第三页 S1–S10）重做：按服务商分组列出用法，每行只显示自己的状态，所有修改只通过底部保存条保存。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `ai-platform/tenant-ai-settings`：按连接方式分别保存、订阅登录状态的可见范围、平台订阅按套餐授权、租户使用平台订阅、页面以连接方式列表呈现；删除「订阅方式只供平台租户」。
- `ai-platform/model-providers`：订阅方式可用于获授权公司的任务。

## Impact

- 数据库：迁移 `V1.3.21`（新表 `sys_ai_provider_config`，旧配置迁入，`sys_ai_config` 只保留当前选择）、`V1.3.22`（按钮资源 110215，只给平台内置管理员角色，不加入任何套餐）。均有回退 SQL。
- 后端：`modules/aitask` 的配置服务、接口与 DTO；`EffectivePermissionResolver` 增加读取公司套餐资源的方法。
- 编排服务：不改（任务仍携带完整配置，订阅状态接口沿用）。
- 前端：`pages/system/ai-settings` 重做。
- 无新增环境变量。前置修复：菜单写操作只允许平台（[zhul-cloud/zhul-trade-erp#66](https://github.com/zhul-cloud/zhul-trade-erp/pull/66)，openspec `menu-write-guard`），否则租户可自行改资源恢复订阅权限。
- 起草：V2 规划与原型由 Codex 完成（Codex 任务 `01a12eed-7238-7b70-b1b7-95980cf9b739`），Claude 接手后按用户确认的 V3 原型与三项调整（不自动切换、去掉编排服务启动前回查授权、菜单写保护单独先修）改写。
