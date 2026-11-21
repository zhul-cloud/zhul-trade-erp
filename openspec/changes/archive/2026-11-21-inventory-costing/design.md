## Context

- 库存底座（inventory-foundation）见归档的 `2026-11-21-inventory-foundation/design.md`。与本变更相关的现状：
  - `inv_batch` 记 `currency_code`、`unit_price`（原币）、`exchange_rate`（下单汇率）、`unit_price_cny`（= 原币 × 下单汇率）。只有货款，没有开票、费用、实际汇率、折价。
  - `inv_move` 有 `move_time`（系统时间），没有业务日期；`amount_cny` = 数量 × 批次 `unit_price_cny`。台账按 `move_time` 归月。
  - 所有过账走 `InventoryPosting.post(lines)`，反向走 `InventoryPosting.reverse(originals, biz)`；`Doc` 记业务类型、来源单据。
  - 出运时 `InventoryHooks.shipped` 记「销售出库」流水（货代仓出库），撤销出运为 `SALES_OUT_UNDO`。
- 订单成本 `OrderCostService` → 纯计算 `OrderCostCalc.compute`：采购四项（货款、开票、采购费用、质量索赔）按采购订单行推算，`actualRate(Po)` 给出外币实际汇率。批次成本必须复用这套算法，口径才一致。
- 外币实付由付款单、作废付款、供应商退款更新，之后都会调用 `PayableSyncService.syncPurchaseOrder(poId)`。采购订单的费用、开票、优惠在采购订单保存时变化，也会同步应付。折价接收与重新打开在到货差异处理里，已调用 `InventoryHooks.discountAccepted / discountReopened`。
- 租户配置存 `sys_config`；后台任务按租户 `TenantContext.runAs` 执行（见期初 `InventoryOpening`）。

## 已定口径（用户 2026-11-21）

| 事项 | 结论 |
|---|---|
| 批次成本 | 货款（外币实际汇率）+ 开票成本 + 采购费用 − 质量索赔；运费、费用单、收款手续费不进库存 |
| 成本变化 | 自动生成成本调整：在库调库存金额，已出运调销售成本，记当前未结账月份，可追溯触发单据 |
| 订单成本 | 已出运数量读结转成本，未出运照旧；系统外入库（0 成本）回退采购订单单价 |
| 月结 | 锁库存与成本；保存台账与销售成本快照；管理员可反结最近一个月 |

## Goals / Non-Goals

**Goals:**
- 批次成本与订单成本的采购四项同一算法：契约测试比对「全部出运后订单成本 = 结转 + 调整」，与改动前的订单成本一致（系统外入库除外）。
- 成本调整可重复执行：同样的单据状态重算不产生新的调整。
- 结账锁定在过账入口统一检查，不靠各业务页面各自判断。

**Non-Goals:**
- 移动加权平均、标准成本；退货入库批次、系统外入库批次随原批次调整。
- 会计凭证、总账科目；财务单据按月锁定。
- 订单进度、利润统计、经营统计的口径变化（它们读订单成本，自然跟随）。

## Decisions

1. **批次成本组成存在批次上。** `inv_batch` 增加 `cost_goods`、`cost_invoice`、`cost_fee`、`cost_claim`（人民币单位成本，6 位），`unit_price_cny` 改为四者之和（保留列名，含义变为单位成本，现有读它的代码不改）。原币 `unit_price` 与下单 `exchange_rate` 照旧记录。
   - 为什么分项存：订单成本明细要分项显示，销售成本快照也要分项；只存合计就得在读时反推。
   - 备选：只存合计 + 读时按采购订单比例拆。拒绝：比例随费用变化，历史结转无法还原。

2. **批次成本计算 `InventoryCosting`。** 输入一组采购订单 ID，复用 `OrderCostCalc.actualRate` 与同样的分摊比例（行货款 ÷ 采购订单货款合计），按订购数量算出每个采购订单行的货款、开票、费用单位成本；质量索赔按入库行的折价金额 ÷ 批次数量。抽出 `OrderCostCalc` 里按行分摊的部分为可复用的静态方法，订单成本与批次成本共用，避免两份公式。

3. **流水记分项金额。** `inv_move` 增加 `amount_goods`、`amount_invoice`、`amount_fee`、`amount_claim`（2 位）。销售出库等出入库流水按批次当时的四项单位成本 × 数量分别保留 2 位，`amount_cny` 为四项之和（保证分项合计等于总额，尾差不再单独处理）。
   - 订单型号行的销售成本 = 该行销售出库流水（`target` 为该型号行、业务类型 SALES_OUT / SALES_OUT_UNDO）的分项合计 + 成本调整明细里指向这些出运的分项合计。

4. **成本调整单。** 新表：
   - `inv_cost_adjust`（表头）：调整单号 CA+日期+序号、调整日期、所属月份（YYYY-MM）、触发原因（1 外币付款、2 作废付款、3 供应商退款、4 采购订单费用变化、5 折价接收、6 重新打开折价、7 每日核对、8 成本口径升级）、来源单据类型 / ID / 单号、三部分合计、备注；业务表（tenant_id、软删除、审计列）。
   - `inv_cost_adjust_item`（明细）：批次、商品、原 / 新单位成本及四项、部分（1 在库、2 已出运、3 其他出库）、仓库或出运单 / 订单型号行 / 出库单、数量、四项金额与合计。一个批次拆成多行。
   - 在库部分同时写 `inv_move`：操作类型 9「成本调整」、数量 0、`amount_*` 为差额、业务类型 `COST_ADJUST`、来源为调整单；`InventoryPosting` 放行数量 0 的这类流水，不动余额。
   - 拆分数量：在库 = 该批次各仓库 `inv_balance` 结存（合格 + 不良）；已出运 = 该批次 SALES_OUT 流水按出运单 × 型号行汇总（扣 UNDO）；其他出库 = 该批次 OTHER_OUT / PURCHASE_RETURN_OUT / 冲销采购入库以外的出库流水按单据汇总。三者之和应等于批次入库数量，不等时以实际流水为准并记 WARN。
   - 冲销采购入库的数量不参与拆分（货已退出，入库被撤销）。

5. **触发与幂等。** `InventoryCosting.recost(poIds, reason, source)` 在调用方事务里执行：重算这些采购订单下全部采购批次的新单位成本，与批次现值比较，有差异的生成一张调整单并更新批次。调用点：
   - `PayableSyncService.syncPurchaseOrder` 之后（付款、作废付款、退款、采购订单保存都会走到；原因由调用方传入，缺省按「采购订单费用变化」）；
   - `InventoryHooks.discountAccepted / discountReopened`；
   - 入库时 `receiptAccepted` 直接按新口径建批次，不生成调整；
   - 每天 02:30 的定时任务按租户（`TenantContext.runAs`）重算最近 180 天有入库的采购订单，原因「每日核对」；
   - 上线启动任务按租户重算全部采购批次一次，原因「成本口径升级」，`sys_config` 记 `inventory.costing.upgraded`。
   - 幂等：只比较数值，没有差异就不写；同一事务内同一批次只调整一次。

6. **业务日期。** `inv_move` 增加 `biz_date`（date，非空，迁移时 = DATE(move_time)，加索引）。`Doc` 增加 `bizDate`，各过账调用点传单据日期（采购入库 `receivedDate`、交货代日期、出运 `shippedDate`、退货入库验收日期、采购退货寄出日期、其他出库 `outDate`）；反向流水与成本调整取当天。台账、销售成本、月结查询都按 `biz_date`。

7. **结账锁定统一在过账入口。** 新表 `inv_period`（tenant、月份 YYYY-MM、状态 1 已结账 / 0 未结账、结账人与时间、反结账人、时间与原因）。`InventoryPosting.post` 对每一行（成本调整除外）检查 `biz_date` 所在月份是否已结账；`reverse` 检查被冲销流水的 `biz_date`。命中则抛 `BizException.of("PERIOD_CLOSED", "2026-11 已结账，不能{动作}业务日期在 2026-11 的单据", {month})`，动作文字由 `Doc` 的业务类型映射。整个业务动作在同一事务，自然回滚。
   - 为什么不在各业务服务里检查：过账入口有十几个，漏一个就锁不住；过账层一定会经过。
   - 单据改日期（如出运单改出运日期）：过账层看不到「改」，所以在这几个可改日期的服务里额外检查新旧日期（出运单、其他出库单目前不支持改日期，只有采购入库与出运需要核对，任务里逐个确认）。

8. **月结快照。** `inv_period_ledger`（月份 × 仓库 × 商品：期初、入库、出库、成本调整、期末的数量与金额）与 `inv_period_cogs`（月份 × 订单型号行 × 出运单：数量、四项金额，以及当月成本调整）。结账时由与台账、销售成本相同的查询生成；反结账软删除快照。台账查询遇到已结账月份读快照。

9. **订单成本改读结转。** `OrderCostService` 额外查每个型号行的「已出运数量与结转分项（含调整）」及其中系统外入库批次的数量，作为 `OrderCostCalc.Input` 的新字段；`compute` 里该行先扣掉已出运数量：已出运部分用结转分项（系统外入库数量按采购订单行单价 × 实际汇率与开票、费用比例回退），剩余数量按原三段规则推算。迁移订单（历史快照）不变。

10. **页面与菜单。**
    - 财务管理 → 成本调整（`/finance/cost-adjustments`、详情 `/finance/cost-adjustments/:id`），按单据页统一规范（销售订单为标准），附件 ownerType `COST_ADJUST`。
    - 财务管理 → 库存月结（`/finance/periods`、详情 `/finance/periods/:month`）。按钮权限：结账 `inventory:period:close`、反结账 `inventory:period:reopen`（只给管理员角色）。
    - 库存台账加「成本调整」金额列与「已结账」标记；订单成本明细的「采购货款」来源里加出运结转与成本调整单。
    - 先出 OpenPencil 原型（C1 成本调整列表、C2 成本调整详情、P1 库存月结列表与结账确认、P2 月份详情三页签、P3 反结账弹窗、L1 台账改动、O1 订单成本来源展开），确认后开发。

## Risks / Trade-offs

- [每次付款都重算整张采购订单的批次] → 一张采购订单的批次通常个位数，成本低；定时核对只看 180 天内。
- [四项分别四舍五入，合计与「数量 × 单位成本」可能差 1 分] → 以四项之和为准，规范里流水金额按四项合计说明；契约测试覆盖。
- [成本口径升级生成一张很大的调整单] → 明细分页显示；只执行一次。
- [结账后才发现漏登的单据] → 只能填原因反结最近一个月，或把单据日期放到当前月；规范已写明。
- [系统外入库成本为 0，库存与销售成本偏低] → 订单成本回退采购单价并提示；库存侧保持 0，结账提醒里列出当月系统外入库数量，便于财务知情。
- [`unit_price_cny` 含义变化] → 读它的地方（库存查询金额、其他出库金额、退货入库成本）本来就要的是单位成本，含义更准确；在迁移与代码注释说明。

## Migration Plan

1. 迁移 V1.5.56：`inv_batch` 加四项成本列（初值：`cost_goods = unit_price_cny`，其余 0）；`inv_move` 加 `biz_date`（= DATE(move_time)）与四项金额（初值：`amount_goods = amount_cny`）；新建 `inv_cost_adjust`、`inv_cost_adjust_item`、`inv_period`、`inv_period_ledger`、`inv_period_cogs`（都在 `TenantTables` 归为私有）；菜单「成本调整」「库存月结」与按钮权限，授给有收款管理菜单的角色，反结账只授管理员。
2. 启动任务「成本口径升级」按租户执行一次，生成调整单。
3. 回退：迁移末尾写手动回退语句（删新表、新列、菜单）；回退后批次单位成本保留新口径的值不影响库存底座使用。
4. 生产上线后看日志「成本口径升级完成，tenantId=…，调整批次数=…」，在成本调整页抽查。

## Open Questions

- 每日核对的时间与回看天数（02:30、180 天）上线后按数据量再调，不影响规范。
