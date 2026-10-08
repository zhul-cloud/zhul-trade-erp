## Context

- 收款记录表 `payment_receipt`：`kind` 1-水单、2-到账，挂在 PI 上（`pi_id`），有 `amount`（原币）、`exchange_rate`、`amount_cny`、`fee_diff`（银行中转差额）、`slip_id`、`status`（有效 / 作废）等；PI 的收款状态与已到账由 `PiSummary` 按有效记录重算。
- 接口：上传水单（按钮权限 `sales:pi:receipt-slip`）、登记 / 作废到账（`sales:pi:receipt-confirm`）、到账登记工作列表（菜单 `/finance/receipts`）。现阶段总经理兼任财务。
- 字典表 `dict_item` 有 `item_value` 字段，可存线上 / 线下标记；平台字典放在 tenant 0。
- 后续「销售订单扩充」要按每单的实收人民币减采购成本算毛利（见用户确认的利润口径）。

## Goals / Non-Goals

**Goals:**
- 三种收款路径（水单确认、先到账后认领、平台收款）记在同一张表里，PI 收款状态统一计算；
- 每笔到账有付款方式（线上 / 线下）、毛额、手续费、实收与实收人民币；
- 收款管理页集中处理与统计。

**Non-Goals:**
- 不对接银行或平台接口自动拉流水（手工登记）；
- 不做应收账龄、对账单与会计凭证（「应收」「对账单」后续变更）；
- 订单毛利的计算与展示放到「销售订单扩充」。

## Decisions

1. **同一张表扩字段，不新建表。** `payment_receipt` 增加：`payment_method`（码值）、`payment_method_name`（名称快照）、`channel`（1-线下、2-线上）、`platform_order_no`、`platform_fee`、`net_amount`（实收原币）、`net_amount_cny`（实收人民币）、`rate_source`（1-系统汇率、2-实际入账）、`payer`、`claimed_by`、`claimed_at`；`pi_id` 改为可空（为空即未认领到账）。原 `amount_cny`（毛额 × 汇率）保留。存量回填：付款方式银行转账、线下，`net_amount = amount`，`net_amount_cny = amount_cny`，汇率来源系统汇率。
   - 备选：未认领到账单独一张表，认领时搬到收款表。搬运会丢失原始记录 ID 与日志关联，取消认领也要反向搬，放弃。
2. **口径。** 已到账（PI 收款进度）= Σ有效到账 `amount`（毛额）；实收 = `amount − platform_fee`；实收人民币 = 实收 × 汇率（系统汇率快照，HALF_UP 2 位），填了实际入账人民币时以填的为准，汇率 = 实收人民币 ÷ 实收（HALF_UP 6 位）。银行中转差额 `fee_diff` 规则不变（它是没到账的部分，不从实收里扣）。
3. **权限。** 新增 PI 下的按钮权限「登记平台收款」（`sales:pi:platform-receipt`）与「认领到账」（`sales:pi:claim-receipt`），迁移时授给已有「上传水单」的角色；未认领到账的登记、取消认领、作废与收款记录用 `sales:pi:receipt-confirm` + 菜单 `/finance/receipts`。平台收款的作废：登记人本人或有 `receipt-confirm` 的用户。
4. **接口。** PI 维度沿用 `/api/v1/sales/pis/{id}/…`：`POST platform-receipts`、`GET claimable-receipts`（同币种未认领到账）、`POST claim`；收款管理维度新增 `/api/v1/finance/receipts`：`GET unclaimed`、`POST unclaimed`、`POST {id}/unclaim`、`POST {id}/void`、`POST records`（列表 + 汇总）。
5. **并发。** 认领：先锁 PI（现有 PI 行锁），再 `SELECT … FOR UPDATE` 锁收款记录，确认仍未认领、未作废后写入；取消认领同样先锁原 PI 再锁记录。平台订单号重复在锁内按「同租户、同付款方式、有效记录」检查。
6. **菜单。** `resource` 100085 改名「收款管理」，地址不变；前端页面在原到账登记基础上改为三个页签（待确认、未认领到账、收款记录）。

**事务边界：** 每次登记、认领、取消认领、作废在一个事务内完成，并在同一事务里调用 `PiSummary.refresh` 重算 PI 收款状态。

**精度：** 金额两位小数 HALF_UP；汇率 6 位小数；实收人民币以入库时舍入后的值为准，统计按已入库的值相加。

## Risks / Trade-offs

- [`pi_id` 改为可空，现有按 PI 查询的地方可能读到未认领记录] → 现有查询都带 `pi_id = ?`，NULL 不会匹配；新增查询显式加 `pi_id IS NULL` / `IS NOT NULL`。
- [业务员能看到全部未认领到账（金额、付款人）] → 认领需要从全部未认领中找自己的单，这是现有「群里通知」流程的线上化；列表只给认领所需的字段。
