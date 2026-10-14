## Context

- 采购订单 `purchase_order` 现在有 `tax_included`、`tax_rate`；行上有 `net_price_cny`，按 ÷(1 + 税率) 算出，砍价用它；合计 `total_amount` 是应付金额的来源。
- 到账 `payment_receipt` 有 `amount`（毛额）、`platform_fee`、`fee_diff`、`net_amount`（实收），以及 `exchange_rate`、`amount_cny`、`net_amount_cny`、`rate_source`（1 系统汇率、2 实际入账）。平台收款没有收款账户。
- 开发库：13 张采购订单都没有勾「含税」；18 笔有效到账都是 USD，都按系统汇率记。

## Goals / Non-Goals

**Goals：**
- 采购订单的开票方式和《供应商开票》表一致，开票成本进入合计与应付。
- 每笔外币到账都能得到准确的人民币，不管结汇时攒了多少笔。

**Non-Goals：**
- 不改询价的含税换算；
- 不做银行流水导入；
- 不做汇兑损益凭证；
- 不支持人民币以外的本位币。

## Decisions

1. **开票成本记在采购订单上，不单独建开票表。** 一张采购订单至多一次开票（《供应商开票》表里一单一行），字段直接加在 `purchase_order` 上：
   - `invoice_required`；
   - `tax_point`：`DECIMAL(5,2)`，单位为百分点；
   - `invoice_amount`、`invoice_cost`：`DECIMAL(18,2)`；
   - `expected_invoice_date`、`invoiced_date`；
   - `invoice_status`：0 不开票、1 待开票、2 已开票。

   发票文件用附件，归属类型新增 `PO_INVOICE`。`tax_included`、`tax_rate` 两列保留不删，迁移后不再读写，以后再清理。

2. **砍价与人民币单价。** `net_price_cny` 的含义改为「单价 × 汇率」，不再 ÷(1 + 税率)。迁移脚本对已有采购订单行按新口径重算 `net_price_cny` 与 `bargain_amount`，开发库里没有含税的单，结果不会变。

3. **合计与应付。** 合计 = 货款 + 其他费用 + 开票成本。开票成本放在 `invoice_cost`，不写进其他费用行，免得被当成运费、包装费分摊。`PayableSyncService` 本来就按合计算应付，登记已开票时调整金额或税点，走和修改采购订单同样的重算路径。

4. **结汇的数据模型。**
   - `fin_fx_settlement`：编号 JH、币种、结汇日期、结汇金额、入账人民币、汇率（6 位）、入账账户、说明、状态（1 有效、2 已作废）、作废原因。
   - `fin_fx_settlement_allocation`：`settlement_id`、`receipt_id`、分摊的原币金额、分摊的人民币金额。
   - `payment_receipt` 新增 `settled_amount`（已结汇原币）、`settle_status`（0 不适用、1 未结汇、2 部分结汇、3 已结汇）。人民币到账的 `settle_status` 为 0。

   分摊的人民币按「分摊金额 × 汇率」计算，尾差放在最后一笔，保证分摊合计等于入账人民币。

5. **先进先出的范围是「租户 + 币种」，不按账户。** 平台收款没有收款账户，信保的钱提现后才进外币账户，按账户分池反而对不上。待结汇的顺序按 `receipt_date`、`id` 排序；包括未认领的到账，因为钱已经在账上了。

6. **估算汇率随最新结汇变化。** 估算汇率取该币种最新一张有效结汇单的汇率。每次登记或作废结汇单后，在同一事务内批量更新该币种所有 `settle_status` 为 1 或 2 的到账的 `exchange_rate`、`amount_cny`、`net_amount_cny`，PI 收款面板、收款记录读到的都是新值。`rate_source` 增加 3「估算」与 4「结汇」：部分结汇的记 3，全部结完的记 4。

7. **只能作废最新的结汇单。** 要作废中间的结汇单，就得重排它后面所有的分摊，容易出错，所以限制只能作废最新一张，由用户从后往前逐张作废。到账作废时，如果已有结汇分摊就拒绝。

8. **并发控制。** 登记和作废结汇单时，先对该币种加 Redisson 锁 `zhul:erp:lock:fx:{tenantId}:{currency}`；登记接口带幂等键。登记到账、作废到账涉及外币时也取同一把锁，避免分摊时余额变化。

9. **存量数据。**
   - `rate_source = 2`（实际入账）的到账，视为已结汇，生成一张迁移专用的结汇单：编号带 `MIG`，每笔到账一张，汇率为原汇率，不参与先进先出；
   - 其余外币到账为未结汇。
   - 开发库没有这类数据，迁移脚本仍然要处理。

10. **菜单与权限。**
    - 菜单「结汇单」`/finance/fx-settlements`，授给有「收款单」菜单（100085）的角色；
    - 按钮「登记结汇」`finance:fx:settle`，授给有「登记到账」按钮的角色；
    - `ROLE_ADMIN` 都授。

## Risks / Trade-offs

- **历史估算值会变**：每次结汇后，未结汇到账的人民币会变，PI 和订单上看到的人民币会跟着变。界面上一律标「未结汇 · 估算」。
- **未认领到账也参与先进先出**：一笔未认领到账可能先被结汇，之后再被认领，这时它的人民币已经确定，认领到哪张 PI 都一样，符合「钱已结」的事实。
- **接口不兼容**：采购订单不再接收 `taxIncluded`、`taxRate`，前后端同时发布；旧的契约测试同步修改。
