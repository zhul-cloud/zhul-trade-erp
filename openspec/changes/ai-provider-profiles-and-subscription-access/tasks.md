# Tasks

## 1. 原型

- [x] 1.1 OpenPencil 原型 V3（连接方式列表）S1–S10，用户 2026-10-29 确认；同时确认三项调整：不自动切换、去掉编排服务启动前回查授权、菜单写保护单独先修（#66）。

## 2. 数据库

- [ ] 2.1 `V1.3.21__ai_provider_profiles.sql`：新表 `sys_ai_provider_config`、复制旧配置（密文原样）、旧列弃用注释；回退 SQL。验证：本机迁移后旧的 api + claude 配置可读，测试库回退后表已删除。
- [ ] 2.2 `V1.3.22__ai_subscription_permission.sql`：资源 110215 只给平台 `ROLE_ADMIN`；回退 SQL。验证：现有套餐不包含 110215。

## 3. 后端

- [ ] 3.1 实体与 Mapper、`AiSubscriptionAccess`（平台始终可用；租户按套餐与资源状态），`EffectivePermissionResolver` 公开读取公司套餐资源。
- [ ] 3.2 配置服务与接口按 design 决策 4 改写；DTO 改为当前选择 + 各连接方式设置。
- [ ] 3.3 `resolveForCurrentTenant` / `isConfiguredForCurrentTenant` 按决策 5；`AiUnavailableException`。
- [ ] 3.4 契约测试：两家 Key 同时保存、切换不覆盖；租户未授权不能保存订阅、状态接口 403；获授权后能选可用订阅、不能选不可用订阅、状态响应无登录命令；收回授权后创建任务被拒且不建任务；订阅任务用平台设置且无 Key；平台维护订阅设置；Key 不出现在响应中。

## 4. 前端

- [ ] 4.1 `pages/system/ai-settings` 按 V3 原型重做：分组列表、行内 Key 编辑、右侧随选中行切换（API 可编辑 / 租户订阅只读 / 平台订阅可编辑）、统一保存条、租户只显示可用与否。
- [ ] 4.2 组件测试：切换后保存条出现且一次保存选择与该行设置；未授权不显示订阅行；租户订阅只读；不可用订阅不可选；平台看到登录命令、租户看不到。

## 5. 文档与验证

- [ ] 5.1 部署文档：个人订阅说明改为「平台按套餐授权给公司使用」，写明授权方法与风险。
- [ ] 5.2 后端相关测试、前端 lint/test、`openspec validate --strict` 通过；本机浏览器对照原型实测平台与获授权租户。

## Workflow follow-up

- PR 发往 `release/1.3.0`，写明迁移 `V1.3.21`、`V1.3.22` 及回退、依赖 #66、无新增环境变量、前后端同批上线。
