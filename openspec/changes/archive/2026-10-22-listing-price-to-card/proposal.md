## Why

资料任务上传资料包时，「## 平台参考价」六列表格只进了卡片下方的「公开挂牌参考」，商品卡片上的平台参考价仍是「未维护」，还要人工再填一遍。开发库里 9 个商品有挂牌价，卡片参考价一条都没有。

## What Changes

- 资料包导入「## 平台参考价」时，如果文件里没有可写入的「## 商品平台参考价」，同时生成商品卡片的平台参考价（一个商品一条，替换现有）。
- 取哪一行：价格类别写了「不代表目标型号」的类目参考行不算；成色为全新的行里取最低价；没有全新时，取所有行的最低价。
- 币种 USD；汇率取导入人所在公司的系统美元汇率，没有设置时显示「未计算」；价格来源写平台、成色与价格类别；取价日期按 frontmatter 的 `updated`。
- 预览提示会生成卡片参考价，并写明取了哪一行、用的汇率。
- 文件里有「## 商品平台参考价」时，卡片仍按那一节写入，不从挂牌价生成。
- 顺带修正：发品任务详情里「参考素材」和「本型号的发品素材」的【全部下载】原来逐个下载，浏览器只放行第一个文件；现在改成打包 zip。新增接口 `GET /operations/listings/{id}/media/zip?source=reference|own`。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `product/product-content`：商品内容规范 v2 的平台参考价导入规则。

## Impact

- 后端：`ContentWriter`、`ContentImportService` 预览提示，`ExchangeRateService` 新增 `find`（不抛异常的取汇率），`ListingTaskService`/`ListingTaskController` 打包。
- 前端：`operations/listings` 全部下载改为 zip。
- 文档：《商品内容规范 v2》、资料包模板。
- 数据：不迁移。已导入过的商品需要重新上传资料包才会生成卡片参考价。
