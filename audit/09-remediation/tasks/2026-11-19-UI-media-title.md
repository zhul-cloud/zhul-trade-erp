# UI-104 商品档案上传图片、视频的标题默认用文件名

## 结论 / 下一步

已修复并本机验证：上传时标题留空，后端用文件名（去掉路径和扩展名，最长 128 字）作标题；填了标题的照旧。下一步：合入 develop，随下一版发布后由用户在生产验收。

## 范围与负责人

| 字段 | 内容 |
| --- | --- |
| 负责人 / 协作者 | Claude（实施与验证）；用户（报告与验收） |
| 关联 ID / 原专题 | UI-104（用户 2026-11-19 反馈） |
| 目标与影响范围 | 商品档案「图片与视频」上传：标题不填时存为空、列表显示「未命名图片 / 视频」。改为默认用文件名。已上传的存量记录不回填；外部链接登记不涉及 |
| 起点 commit / 工作分支 | develop `e3156764` / `fix/media-title-default-filename` |
| OpenSpec | 不适用：小缺陷修复 |
| PR / 目标分支 | 待创建 → `develop` |
| 合并证据 | 未合并 |
| 目标版本 / 环境 | 下一版 / 生产 |

## 实施与验证结果

- 后端 `ProductMediaServiceImpl.upload`：标题为空时取 `titleFromFileName(originalFilename)`，网页和其他调用方都生效。
- 前端 `MediaCard.tsx`：标题输入框提示「不填则用文件名」。

| 命令 / 操作与证据 | 环境、commit / 版本 | 预期 | 实际 | 结论 |
| --- | --- | --- | --- | --- |
| `mvn -o test -Dtest=ProductMediaServiceImplTest,ProductApiContractTest`（新增 `blankTitleDefaultsToFileNameWithoutExtension`、`titleFromFileName_stripsPathAndExtension`） | 本机 | 空标题取文件名、去路径与扩展名、超长截断，填了的不变 | 23/23 通过 | 通过 |
| `npm run lint`、`npx antd lint ./src`、`npx jest src/pages/product` | 本机 | 无错误 | 通过（66 例） | 通过 |

## 合并、部署与逐 ID 验收

| ID | 修复 / 验收范围 | 部署与目标环境验收证据 | 验收人 / 日期 | 结论与剩余项 |
| --- | --- | --- | --- | --- |
| UI-104 | 商品档案上传图片 / 视频不填标题，卡片显示文件名 | 未部署 | — | 待合并 |

## 事件记录

| 日期 / 作者 | 事件与证据 | 下一步 / 交接 |
| --- | --- | --- |
| 2026-11-19 / Claude | 用户反馈，实施并本机验证 | 提 PR 合入 develop |
