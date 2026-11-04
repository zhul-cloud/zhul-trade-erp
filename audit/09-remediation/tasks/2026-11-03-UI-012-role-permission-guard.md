# UI-012 角色启停与权限分配误改权限（批次 C）

## 范围与负责人

| 字段 | 内容 |
| --- | --- |
| 负责人 / 协作者 | Claude（实施与验证）；用户 Ezio 决定合并、发布与验收 |
| 关联 ID / 原专题 | [UI-012](../../02-frontend/02-表单草稿与页面切换.md)、[UI-013](../../02-frontend/02-表单草稿与页面切换.md)、[UI-014](../../02-frontend/02-表单草稿与页面切换.md) |
| 目标与影响范围 | 角色列表启停改坏数据范围与自定义部门；数据范围 0 的显示与执行不一致；分配权限在加载完成前可保存。涉及后端 `SaveRoleRequest`、`RoleServiceImpl`，前端 `system/role`。不含角色对象归属校验（SEC-003，批次 B）、编辑角色抽屉打开时的同类竞态（另编 UI-037 处理） |
| 起点 commit / 工作分支 | `develop` `f56bdbcd579ee3b7e78de29903b4bada23813a0a`；`fix/role-permission-guard` |
| OpenSpec | 不适用：缺陷修复，不改变规格描述的业务行为 |
| PR / 目标分支 | [zhul-cloud/zhul-trade-erp#131](https://github.com/zhul-cloud/zhul-trade-erp/pull/131)：`fix/role-permission-guard` → `develop` |
| 合并证据 | #131 合并到 `develop`，merge commit `ec3e24bfca3aa3c86ba3d61993abf4cc52b8ba58`（2026-11-03）；未发布 |
| 目标版本 / 环境 | v1.6.0 / 生产 |

## 实施与验证结果

开工前在 `develop` `f56bdbcd` 核对三项仍成立。生产库只读统计 `role.permission_scope`：1 有 8 个、2 有 2 个、3 有 4 个，没有 0。

- **UI-012**：`SaveRoleRequest` 去掉 `permissionScope = 0`、`status = 1` 默认值，编辑时未传即不改；`updateRole` 只有传了范围或部门时才重建自定义部门。列表启停仍只发 `{name, status}`，不再影响数据范围。
- **UI-013**：口径定为 0 是早期缺省值，后端 `DataScopeResolver` 本来就按仅本人执行。前端列表把 0 显示为「仅本人数据」（编辑回填原本就按 3）；后端新建不传范围时存 3。生产无 0 值角色，不做数据迁移。`role.permission_scope` 列注释仍写「0-无权限」，与执行不符，未改（改注释需迁移，收益低）。
- **UI-014**：打开「分配权限」时清空上一角色的勾选与菜单树，按请求序号丢弃迟到响应；加载完成前「保存权限」不可点、菜单树显示加载中；加载失败提示后关闭抽屉。只是状态变化，没有新增界面元素，未另出原型。

| 命令 / 操作与证据 | 环境、commit / 版本 | 预期 | 实际 | 结论 |
| --- | --- | --- | --- | --- |
| `mvn test -Dtest=RoleScopeContractTest`（[测试源码](../../../zhul-erp-backend/src/test/java/com/zhul/erp/modules/system/RoleScopeContractTest.java)） | 本机 Docker 测试库；后端代码临时退回修复前 | 启停与新建用例失败 | 2 个失败：启停后范围 1→0、新建存 0；「显式改范围」用例新旧代码都通过（防回归） | 缺陷见证成立 |
| 同上 | 本机；`fix/role-permission-guard` 工作区 | 3 个通过 | 3 个通过 | 通过 |
| `npx jest src/pages/system/role`（[测试源码](../../../zhul-erp-frontend/src/pages/system/role/index.test.tsx)） | JSDOM；角色页临时退回修复前 | 失败 | 3 个失败 | 缺陷见证成立 |
| 同上 | JSDOM；工作区 | 3 个通过 | 3 个通过 | 通过 |
| `mvn test`（后端全量） | 本机；工作区 | 无失败 | 1203 个，0 失败，6 个环境跳过 | 通过 |
| `npm run lint`、`npx antd lint ./src`、`npx jest`、`npx biome check src/pages/system/role` | 本机；工作区 | 无问题 | 通过；antd 389 文件无问题；22 套件 161 个通过 | 通过 |
| 浏览器操作角色页 | — | — | 未执行 | 上线后验收 |

## 合并、部署与逐 ID 验收

| ID | 修复 / 验收范围 | 部署与目标环境验收证据 | 验收人 / 日期 | 结论与剩余项 |
| --- | --- | --- | --- | --- |
| UI-012 | 范围 1/2/3 启停两次不变；自定义部门保留；显式改范围仍重建部门 | v1.6.0 已部署（[生产发版](https://github.com/zhul-cloud/zhul-trade-erp/actions/runs/41653028814)，2026-11-03 22:28 部署成功） | — | 已发布待验收 |
| UI-013 | 0 显示为仅本人；新建缺省存 3 | v1.6.0 已部署（[生产发版](https://github.com/zhul-cloud/zhul-trade-erp/actions/runs/41653028814)，2026-11-03 22:28 部署成功） | — | 已发布待验收；列注释未改 |
| UI-014 | 加载完成前不能保存；迟到响应不覆盖当前角色 | v1.6.0 已部署（[生产发版](https://github.com/zhul-cloud/zhul-trade-erp/actions/runs/41653028814)，2026-11-03 22:28 部署成功） | — | 已发布待验收 |

## 阻塞与下一步

- 发 PR 到 `develop`，由用户决定合并；随 v1.6.0 发布后在生产角色页验收启停、编辑、分配权限。

## 事件记录

| 日期 / 作者 | 事件与证据 | 下一步 / 交接 |
| --- | --- | --- |
| 2026-11-03 / Claude | 认领批次 C，复核三项仍成立；在 `fix/role-permission-guard` 实施并完成上表验证 | 发 PR 到 `develop` |
| 2026-11-03 / Claude | #131 合并到 `develop`（`ec3e24bf`）；台账三项改为「已合并待发布」 | 随 v1.6.0 发布后验收 |
| 2026-11-03 / Claude | #131 随 v1.6.0 发布（release/1.6.0 #135，Kevin 发版），生产部署成功；台账改为「已发布待验收」 | 待线上验收 |
| 2026-11-04 / Claude | UI-012、013、014 在本机用生产备份的脱敏副本跑 `v1.7.1` 验收通过，证据见 [本机验收记录](2026-11-04-ACCEPT-local-v171.md)；不是生产环境验证 | 用户决定是否据此关闭 |
