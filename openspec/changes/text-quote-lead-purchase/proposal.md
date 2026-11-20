## Why

文字报价的报价货期只有「按采购货期顺延」和固定一档两种。有时业务员想直接按采购报的货期报给客户，不想往后挪一档，现在只能逐个改报价单行或手改文字（用户 2026-11-20 反馈）。

## What Changes

- 文字报价弹窗的「报价货期」下拉在「按采购货期顺延」后面增加「按采购货期」：每个有货行原样取采购回价的货期，没有回价时取报价单行的货期。
- 默认仍为「按采购货期顺延」，只影响本次生成的文字，不改报价单。
- 文字报价接口的 `leadTime` 参数增加取值 `purchase`。

## Capabilities

### New Capabilities

### Modified Capabilities
- `quotation/quotation`：「文字报价」报价货期增加「按采购货期」。

## Impact

- 后端：`QuoteTextOptions`、`QuotationDocumentServiceImpl.text`。
- 前端：`src/pages/quotation/textOptions.ts`。
- 无数据库迁移，无新增配置项。
