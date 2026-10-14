## Why

营销包上传后任务仍停在「营销待生成」：独立站要英文、俄文都写入才算完成，但默认营销包和 AI 技能只有英文独立站，俄文要另外下载「独立站俄文」再生成一次，实际操作中容易漏掉。

## What Changes

- 默认营销包（全部启用平台）在「## 独立站」之后增加「## 独立站俄文」章节，三级标题与独立站相同，内容写俄文。
- 上传解析：「## 独立站俄文」拆成单独一项（独立站、俄文），按俄文写入独立站，记录俄文写入时间；英文文件里出现该章节不再报语言不符。
- 单独下载「独立站俄文」（`lang: ru`，只含独立站）保留，用于单独重做俄文。
- 《商品内容规范 v2》、AI 技能 `fouwell-product-content` 的输出格式同步加上独立站俄文章节。
- 完成条件不变：独立站仍要英文、俄文都写入。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `product/product-content`：任务包下载的默认营销包含独立站俄文；商品内容规范 v2 增加独立站俄文章节。

## Impact

- 后端：`ContentV2Parser` 识别独立站俄文章节；`ContentPackageService` 默认营销包拼入俄文模版片段；新增模版 `marketing-site-ru.md`。
- 前端：上传预览与写入结果显示「独立站俄文」项；下载菜单说明。
- 文档：《商品内容规范 v2》。
- 技能：zhul-skills 的 `fouwell-product-content` 输出格式与验收。
