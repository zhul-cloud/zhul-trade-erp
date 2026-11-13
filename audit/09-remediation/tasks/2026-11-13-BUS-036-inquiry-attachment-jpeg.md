# BUS-036 询盘附件不能上传 .jpeg 图片

## 范围与负责人

| 字段 | 内容 |
| --- | --- |
| 负责人 / 协作者 | Claude |
| 关联 ID / 原专题 | BUS-036（用户 2026-11-13 反馈，新发现） |
| 目标与影响范围 | WhatsApp 下载的图片扩展名是 .jpeg，新建 / 修改 / 追加询盘附件时被前端拦下，提示「只支持图片（JPG、PNG）、PDF、Excel」。期望 .jpeg 与 .jpg 同样可传。涉及前端分片上传组件 `ChunkUploadList` 的扩展名检查；不改后端 |
| 起点 commit / 工作分支 | 26a835fa01d049c08d042c4d56d5b53ae9b5b1d3 / fix/inquiry-attachment-jpeg |
| OpenSpec | 不适用：缺陷修复，规格「询盘大附件」本就允许 JPG 图片 |
| PR / 目标分支 | 待创建 → develop |
| 合并证据 | 未合并 |
| 目标版本 / 环境 | 下一个 release |

## 实施与验证结果

根因：后端 `DirectUploadService.extOfName` 把 jpeg 视为 jpg，完成时再按文件头核对；前端 `checkFile` 直接用原扩展名比对 `exts`（只有 jpg），.jpeg 在选文件时就被拦下。修复：前端 `extOf` 同样把 jpeg 视为 jpg（[ChunkUploadList](../../../zhul-erp-frontend/src/components/ChunkUploadList/index.tsx)），提示文字加上 JPEG。

铭牌图片：前端 `NameplateUploader` 已允许 .jpeg，后端 `UploadRules.check` 按文件头识别为 jpg 并以 jpg 存储，代码路径上没有拦截；本轮未在浏览器实测，留待走查确认。

| 命令 / 操作与证据 | 环境、commit / 版本 | 预期 | 实际 | 结论 |
| --- | --- | --- | --- | --- |
| `npx jest src/components/ChunkUploadList`（新增用例「.jpeg 按 jpg 放行」） | 本机 jest | 5 个用例通过 | 5 passed | 通过 |
| `npx biome check`、`npx tsc --noEmit` | 本机 | 无错误 | 无错误 | 通过 |

## 合并、部署与逐 ID 验收

| ID | 修复 / 验收范围 | 部署与目标环境验收证据 | 验收人 / 日期 | 结论与剩余项 |
| --- | --- | --- | --- | --- |
| BUS-036 | 新建询盘上传 WhatsApp 的 .jpeg 附件与铭牌 | 未部署 | — | 待合并、发布与验收 |

## 阻塞与下一步

合并到 develop 后随下一个 release 发布；发布后用 .jpeg 图片验收附件与铭牌。

## 事件记录

| 日期 / 作者 | 事件与证据 | 下一步 / 交接 |
| --- | --- | --- |
| 2026-11-13 / Claude | 认领并修复，单元测试通过 | 创建 PR |
