## Decisions

- **字段**：`pay_later tinyint(2) NOT NULL DEFAULT 0`（0-否、1-先采购后收款），`pay_later_reason varchar(200) NOT NULL DEFAULT ''`，`pay_later_by bigint NULL`（操作人用户 ID，与订单「取消人」`cancelled_by` 一致，详情显示姓名），`pay_later_at datetime NULL`。
- **转订单请求**：`ConvertOrderRequest` 加 `payLater`、`payLaterReason`。PI 收款状态为「未付款」时必须 `payLater=true` 且原因不为空；PI 有水单或到账时忽略这两个字段（不记为先采购）。
- **销售日期**：没有水单和到账时默认当天（现有逻辑）。
- **提醒何时消失**：前端按订单收款状态判断，不再是「未付款」即不显示提醒；不另存「已提醒」状态。
- **筛选**：订单查询加 `payLaterOpen=true` → `pay_later = 1 AND receipt_status = 未付款`。
- **「按 PI 创建」的候选列表**不变，仍只列有水单或到账的 PI；未付款的 PI 从 PI 详情页转，避免在新建订单入口把没付款的 PI 混进来。
- **不加权限**：用户确认原因必填、留痕即可；后续需要时再加按钮权限。

## 事务边界

先采购后收款字段与订单、采购需求、报价单成交推进在现有转订单事务内一起写入。

## 迁移

V1.5.3：加四列与 `pay_later` 索引；回退为删除索引与四列。
