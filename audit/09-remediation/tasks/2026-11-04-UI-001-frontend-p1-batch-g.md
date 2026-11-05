# 批次 G：前端 P1（UI-001、002、003）

## 范围与负责人

| 字段 | 内容 |
| --- | --- |
| 负责人 / 协作者 | Claude（实施与验证）；用户 Ezio 验收 |
| 关联 ID / 原专题 | [UI-001、UI-002](../../02-frontend/01-接口重放与附件上传.md)、[UI-003](../../02-frontend/02-表单草稿与页面切换.md) |
| 目标与影响范围 | 前端：附件上传组件、请求拦截器、「我的询价」页。没有后端改动，没有迁移，没有配置项 |
| 起点 commit / 工作分支 | develop `075a986d`；分支 `fix/ui-002-replay-check`、`fix/ui-001-multi-upload`、`fix/ui-003-leave-guard` |
| OpenSpec | 不适用：缺陷修复 |
| PR / 目标分支 | [#172](https://github.com/zhul-cloud/zhul-trade-erp/pull/172)（UI-002）、[#173](https://github.com/zhul-cloud/zhul-trade-erp/pull/173)（UI-001）、[#174](https://github.com/zhul-cloud/zhul-trade-erp/pull/174)（UI-003），均发往 `develop`，CI 通过后自动合并 |
| 合并证据 | `develop`：#172 `984edecb`、#173 `315a5e00`、#174 `bac806cf` |
| 目标版本 / 环境 | 随下一版发布 |

## 实施与验证结果

每项都先在修复前的代码上运行缺陷见证测试，确认按审查描述失败，再在修复后确认通过。

| ID | 修复 | 缺陷见证（修复前 → 修复后） | PR / merge commit |
| --- | --- | --- | --- |
| UI-002 | 令牌刷新后的重放响应在拦截器里原地做业务码校验：`code=0` 或非统一格式照常返回；其他业务码按真实原因抛出 `BizError`；仍是 401 按登录过期处理；重放的 HTTP 错误按原错误抛出 | [`requestReplay.test.ts`](../../../zhul-erp-frontend/src/requestReplay.test.ts) 6 个用例：修复前 3 个失败（重放 403 被当成功、重放 500 退出了刚刷新的登录、重放仍 401 时把原响应返回）→ 修复后全部通过；并发 401 只刷新一次、刷新失败不进成功回调 | [#172](https://github.com/zhul-cloud/zhul-trade-erp/pull/172) `984edecb` |
| UI-001 | 新增 `useAttachmentList`：增删总是基于最新列表，组件卸载后迟到的上传结果不写回。`AttachmentWall` 与回价附件都改用它；`UploadTile` 新增 `remaining`，一次多选超出剩余名额（含正在上传的）的不上传并提示 | [`attachmentUpload.test.tsx`](../../../zhul-erp-frontend/src/pages/warehouse/attachmentUpload.test.tsx)：挂载真实组件与 antd `Upload`，只 mock 上传接口并控制完成时机。修复前 6 个全失败（原有 1 份、一次选 3 份按 3→1→2 完成后只剩最后一份；上传中删除的附件复活；不限份数）→ 修复后全部通过 | [#173](https://github.com/zhul-cloud/zhul-trade-erp/pull/173) `315a5e00` |
| UI-003 | 「我的询价」新增离开守卫：有没保存的修改时，点别的任务、搜索与清空搜索、切页签前弹出「保存草稿 / 放弃修改 / 取消」；保存成功才切换，草稿不完整或保存失败时留在原任务，放弃只丢当前任务的修改；关闭或刷新页面时浏览器提示。`save` 改为返回是否保存成功 | [`leaveGuard.test.tsx`](../../../zhul-erp-frontend/src/pages/inquiry/my-tasks/leaveGuard.test.tsx)：挂载整个页面，只 mock 接口，7 个用例。修复前 6 个失败（切换时没有任何提示，输入被替换）→ 修复后全部通过 | [#174](https://github.com/zhul-cloud/zhul-trade-erp/pull/174) `bac806cf` |

- **回归**：每个 PR 都跑了 `npm run lint`（Biome + tsc）、全量 `npx jest`（最后一次 24 个测试文件、172 个用例）和改动文件的 `antd lint`，全部通过。
- **未覆盖**：
  - 三项都没有做真实浏览器验收，验收方式待定：可在本机脱敏副本上验，也可上线后在生产验。
  - UI-003 只覆盖页面内的切换。离开页面去别的菜单属于 UI-004，不在本批。
  - UI-003 的确认框沿用项目现有的 `modal.confirm` 样式，只多一个「放弃修改」按钮，没有新增界面样式，因此没有走原型流程。

## 合并、部署与逐 ID 验收

| ID | 修复 / 验收范围 | 部署与目标环境验收证据 | 验收人 / 日期 | 结论与剩余项 |
| --- | --- | --- | --- | --- |
| UI-001 | 多选附件全部保留；上传中删除不复活；不超名额 | v1.8.0 已部署（[#181](https://github.com/zhul-cloud/zhul-trade-erp/pull/181) `75c325bd`，生产发版运行 41950405348 成功，2026-11-05）；生产未验收 | — | 已发布待验收 |
| UI-002 | 重放的业务错误按真实原因拒绝；重放 HTTP 错误不退出登录 | v1.8.0 已部署（[#181](https://github.com/zhul-cloud/zhul-trade-erp/pull/181) `75c325bd`，生产发版运行 41950405348 成功，2026-11-05）；生产未验收 | — | 已发布待验收 |
| UI-003 | 切换任务、搜索、切页签前先确认 | v1.8.0 已部署（[#181](https://github.com/zhul-cloud/zhul-trade-erp/pull/181) `75c325bd`，生产发版运行 41950405348 成功，2026-11-05）；生产未验收 | — | 已发布待验收 |

## 事件记录

| 日期 / 作者 | 事件与证据 | 下一步 / 交接 |
| --- | --- | --- |
| 2026-11-04 / Claude | 认领 UI-001、002、003；逐项完成缺陷见证与修复验证，提 #172、#173、#174 | 随下一版发布后验收 |
| 2026-11-04 / Claude | #172、#173、#174 CI 三项通过后合并到 `develop`；批次 G 三项已合并。同日按用户反馈新增 UI-041（表格低分辨率布局），待认领 | 随下一版发布后验收；UI-041 待用户确认最低分辨率与排查方式 |
| 2026-11-05 / Claude | 随 v1.8.0 发布到生产：[#181](https://github.com/zhul-cloud/zhul-trade-erp/pull/181) 合并到 `main`（`75c325bd`），生产发版运行 41950405348 构建与部署（含健康检查）成功；合回 develop 见 [#182](https://github.com/zhul-cloud/zhul-trade-erp/pull/182) | 生产验收后关闭（验收人与日期补进上表） |
