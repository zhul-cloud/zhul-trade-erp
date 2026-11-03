## Decisions

- **字段**：`quotation_item.no_stock_reason tinyint NOT NULL DEFAULT 0`（0-非无货行、1-暂时缺货、2-已停产、3-停产有替代型号、4-其他），`no_stock_note varchar(100) NOT NULL DEFAULT ''`（原因为「其他」时的英文说明）。替代型号沿用现有 `replacement_model`。
- **未回价型号按无货报**：可报价判断（`QuoteCandidates.Candidate.pickable`）对「询价中」的型号也返回可选，但标记 `pendingAsNoStock`；带入时该行 `no_stock=1`、原因 1。版本、锁定、成交推进规则不变：这一行算作已报过；之后回价，业务员对已发送的报价单「修改（出新版本）」，在该行填售价即为正常报价。询价任务、询盘回价进度不受影响。
- **询盘状态**：询价中的询盘被全部报出（含按无货报）后，按现有规则进入「已报价」；采购后续回价照常记录。
- **整单无货可发送**：去掉发送校验「报价单的型号都是无货，至少要有一个报价的型号才能发送」；单价仍要求非无货行有售价。整单无货的报价单不能开 PI（开 PI 只取有售价的行，没有时提示「报价单没有可开 PI 的型号」），沿用现有校验。
- **渲染**：`QuotationRenderModels` 按原因输出：1→「No stock」，2→「Discontinued」，3→「No stock」并在描述后加「Discontinued, replacement: X」，4→说明原文。文字报价转小写。存量行原因按回填值输出，结果与现在一致。
- **默认原因**：采购标了停产且有替代型号 → 3；其余 → 1。业务员选 3 时替代型号必填，选 4 时说明必填。

## 事务边界

只涉及报价单自身的保存与发送，在现有报价单事务内完成，不新增跨模块写操作。

## 迁移

V1.5.2：加两列；`UPDATE quotation_item SET no_stock_reason = IF(replacement_model <> '', 3, 1) WHERE no_stock = 1`；回退为删除两列。
