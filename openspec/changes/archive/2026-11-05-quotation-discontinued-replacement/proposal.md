## Why

用户反馈（2026-11-05 第二批第 1 条）：回价时如果是停产有替代的型号，报价时要显示停产，并告诉客户替代（或升级）型号。

现状：采购询价时可以标「停产」并填替代型号，但报价单只在「无货、没填售价」的行写 Discontinued 和替代型号。采购标了停产、按替代型号回了价时，报价单只显示客户问的原型号和价格，客户不知道已经停产、实际报的是另一个型号。

已确认：替代和升级不区分，统一叫「替代型号」。原型见 `docs/03-产品原型/03-业务域/11-05反馈方案二/` R1。

## What Changes

- 从询盘生成报价单时，采购标了停产且填了替代型号的行，不论有没有价格，都带上替代型号。
- 有价格的停产替代行：报价单编辑页在型号下标「停产 · 替代 XXX」，可以修改或清空替代型号；正式报价单描述列追加 `Discontinued, replaced by XXX`，文字报价该行末尾追加 `(discontinued, replaced by XXX)`。
- 无货行的现有写法不变。

## Capabilities

### New Capabilities
（无）

### Modified Capabilities
- `quotation/quotation`：新增「停产替代型号提示」。

## Impact

- 后端：`QuotationServiceImpl` 生成报价行时带替代型号；`QuotationStore` 保存时允许有价格的行保留替代型号；`QuotationRenderModels` 给有价格的替代行加描述后缀和文字报价后缀；`TextTemplateEngine` 输出后缀。
- 前端：报价单编辑页型号下的标记与替代型号编辑；跟进报价单预览同样显示（见 `followup-quotation-preview`）。
- 数据库：`quotation_item.replacement_model` 已有，不改表；没有新配置项。
