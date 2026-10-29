# Tasks

## 1. 实现

- [x] 1.1 `MenuController` 五个写接口加菜单页面与按钮权限校验；`MenuServiceImpl` 写方法限定平台账号，否则 403。
- [x] 1.2 前端 `access.ts` 菜单三个按钮权限改为同时要求平台账号。

## 2. 验证

- [x] 2.1 `MenuWriteGuardContractTest`：租户管理员五类写操作均 403 且数据不变；平台账号缺按钮权限 403；平台管理员可改。原有 `MenuPermissionIntegrationTest`、`AuditFillIntegrationTest`、`EffectivePermissionIntegrationTest` 通过。
- [x] 2.2 前端 lint 通过。
