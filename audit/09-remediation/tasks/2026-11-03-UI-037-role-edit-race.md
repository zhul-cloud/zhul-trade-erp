# UI-037 编辑角色抽屉的迟到回包

## 范围与负责人

| 字段 | 内容 |
| --- | --- |
| 负责人 / 协作者 | Claude（发现、实施与验证）；用户 Ezio 决定合并、发布与验收 |
| 关联 ID / 原专题 | [UI-037](../../02-frontend/02-表单草稿与页面切换.md)（2026-11-03 整改中新发现，修 UI-014 时发现同类问题） |
| 目标与影响范围 | 角色页 `openEdit`：只让最后一次点击生效；点新增或换角色后迟到回包不再打开抽屉；部门加载失败给出提示。只改前端 `system/role/index.tsx` |
| 起点 commit / 工作分支 | `develop` `ec3e24bfca3aa3c86ba3d61993abf4cc52b8ba58`；`fix/role-edit-race` |
| OpenSpec | 不适用：缺陷修复 |
| PR / 目标分支 | [zhul-cloud/zhul-trade-erp#132](https://github.com/zhul-cloud/zhul-trade-erp/pull/132)：`fix/role-edit-race` → `develop` |
| 合并证据 | #132 合并到 `develop`，merge commit `f705aaeec3bae53a4eb38e8992ac99fb4ebeeee7`（2026-11-03）；未发布 |
| 目标版本 / 环境 | v1.6.0 / 生产 |

## 实施与验证结果

`openEdit` 改为按请求序号 `editSeq` 判断：先取部门，确认仍是最后一次请求后，才设置编辑角色、部门并打开抽屉；`openCreate` 让序号加一，作废未返回的编辑请求；部门加载失败提示「角色部门加载失败，请重试」。

| 命令 / 操作与证据 | 环境、commit / 版本 | 预期 | 实际 | 结论 |
| --- | --- | --- | --- | --- |
| `npx jest src/pages/system/role -t UI-037`（[测试源码](../../../zhul-erp-frontend/src/pages/system/role/index.test.tsx)） | JSDOM；角色页临时退回修复前 | 失败 | 失败：关闭后抽屉重新弹出「编辑角色 - 角色乙」 | 缺陷见证成立 |
| 同上 | JSDOM；工作区 | 通过 | 通过 | 通过 |

失败与过程说明：第一版测试场景是「B 抽屉打开时 A 迟到」，修复前也通过（表单初始值只在挂载时读取，界面不变），没有抓到问题，改为「关闭 B 后 A 迟到」后才见证缺陷；改写时一度用 `git checkout` 误还原了未提交的测试文件，已重建并重新验证。全量检查结果见事件记录。

## 合并、部署与逐 ID 验收

| ID | 修复 / 验收范围 | 部署与目标环境验收证据 | 验收人 / 日期 | 结论与剩余项 |
| --- | --- | --- | --- | --- |
| UI-037 | 关闭后不再弹出；只显示最后打开角色的数据；加载失败有提示 | v1.6.0 已部署（[生产发版](https://github.com/zhul-cloud/zhul-trade-erp/actions/runs/41653028814)，2026-11-03 22:28 部署成功） | — | 已发布待验收 |

## 阻塞与下一步

- 发 PR 到 `develop`，由用户决定合并；随 v1.6.0 发布后验收。

## 事件记录

| 日期 / 作者 | 事件与证据 | 下一步 / 交接 |
| --- | --- | --- |
| 2026-11-03 / Claude | 修 UI-014 时发现，按用户要求「审查没列的问题一并处理」新编 UI-037；在 `fix/role-edit-race` 实施并验证 | 发 PR 到 `develop` |
| 2026-11-03 / Claude | 前端全量：`npm run lint` 通过、`npx antd lint ./src` 389 文件无问题、`npx jest` 22 套件 162 个通过 | 发 PR 到 `develop` |
| 2026-11-03 / Claude | #132 合并到 `develop`（`f705aaee`）；台账改为「已合并待发布」 | 随 v1.6.0 发布后验收 |
| 2026-11-03 / Claude | #132 随 v1.6.0 发布（release/1.6.0 #135，Kevin 发版），生产部署成功；台账改为「已发布待验收」 | 待线上验收 |
