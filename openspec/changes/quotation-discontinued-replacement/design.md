## Context

`quotation_item` 已有 `replacement_model` 与 `no_stock_reason`。生成报价行时，只有「无货且采购标停产」的行才复制询盘型号的替代型号；渲染时只有无货行（`isNoStockLine`）才输出替代型号。

## Decisions

### D1 生成报价行
询盘型号 `lifecycle = 停产` 且 `replacement_model` 非空时，报价行一律带上替代型号；无货原因的默认值不变（只对无货行生效）。

### D2 有价格的替代行
「有价格的停产替代行」= 不是无货行、`replacement_model` 非空。业务员清空替代型号即不再提示。手动加的行也可以填替代型号（编辑页同一个入口）。

### D3 客户看到的写法
- 正式报价单（Excel / PDF / 图片）：描述列 = 原描述 + ` · Discontinued, replaced by XXX`（原描述为空时只写后半句），与无货行的写法一致。
- 文字报价：模版行渲染后，在行尾换行前追加 ` (discontinued, replaced by XXX)`；渲染数据新增 `item.replacementNote`，模版不需要改。
- 无货行沿用现有规则，不重复追加。

### D4 不区分替代与升级
统一称替代型号（replacement），不加类型字段。

## Risks / Trade-offs

- 已经生成的草稿报价单不会自动补替代型号；重新从询盘生成或手动填写即可。
