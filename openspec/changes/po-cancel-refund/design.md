## Context

- 取消采购订单：`PurchaseOrderServiceImpl.cancel` 改状态后调用 `PayableSyncService.syncPurchaseOrder`，应付金额归零，已付全部变成 `credit_amount`（供应商欠款），状态「已关闭」但有欠款。
- 付款：`fin_payment` 记录付款方式（1 对公、2 员工垫付、3 私对私）、垫付人 `advance_user`、报销状态 `reimburse_status`（0 不适用、1 待报销、2 已进报销单）；`fin_payment_allocation` 记录核销到哪张应付单，`from_credit` 区分实付（0）、欠款抵扣转入（1）、退款冲减（2）、抵扣转出（3）。
- 已有能力：`PaymentService.voidPayment`（只允许登记人或有付款登记权限的人；已进报销单不能作废；产生的欠款被抵扣过不能作废）、`refundReceived`（只能本人对自己垫付过的应付单登记）、`refund`（财务登记供应商退款）。
- 报销：`ReimbursementService.pending` 汇总五类待报销项；撤回目前只针对已提交的报销单。

## Goals / Non-Goals

**Goals:**
- 取消与收尾在一个事务里完成，不留半截状态。
- 复用现有作废、登记退款的核心逻辑，让应付、付款计划、待报销、操作日志与人工操作时完全一致。

**Non-Goals:**
- 不处理已有发货的采购订单（仍走到货差异 / 采购退货）。
- 不自动登记公司账户的供应商退款（钱是否到账只有财务知道），只做「待退款」标记。
- 不改已撤回 / 已付报销单本身；已报销的通过负数待报销项扣回。
- 待报销里的快递运费、退货运费不提供撤回。

## Decisions

1. **收尾服务放在 finance 模块**：新增 `PurchaseCancelSettlement`（finance/service），由采购取消在 `syncPurchaseOrder` 之后调用，入参为应付单 id、退款选择、操作人。理由：付款、报销规则都在 finance；采购只传选择，不碰付款表。备选「采购服务里直接改付款」会让采购模块跨层写财务表，不采用。

2. **系统调用入口与权限**：把 `voidPayment`、`refundReceived` 的核心抽成内部方法（不校验「当前用户是登记人 / 垫付人」），对外接口保持原校验；收尾服务调用内部方法，并把垫付人显式传入。取消采购订单本身已有采购权限校验，收尾是其后果，不再要求采购员有付款登记权限。

3. **逐笔判定顺序**（只看 `from_credit = 0` 的实付核销）：
   - 垫付 + 待报销 + 这笔付款只核销本应付单 + 它的欠款没被抵扣出去 → 作废付款；
   - 其余垫付（已进报销单，或核销了多张，或欠款已被抵扣）→ 以 `advance_user` 名义登记已收到退款，金额 = 本笔核销到本应付单的金额，且不超过当时欠款；
   - 对公 / 私对私 → 不动，`fin_payable.refund_expected = 1`。
   先处理作废（会减少已付），再按剩余欠款登记退款，避免超过欠款。

4. **「待退款」标记**：`fin_payable.refund_expected tinyint NOT NULL DEFAULT 0`（0-否、1-待退款）。`PayableSyncService.refresh` 在欠款为 0 时清零，所以财务登记退款或欠款抵扣用完后自动去掉。备选「每次查询时现算」需要再存一份取消选择，且无法区分「不退款」，不采用。

5. **采购订单记录选择**：`purchase_order.cancel_refund tinyint NOT NULL DEFAULT 0`（0-无付款或未选、1-会退款、2-不退款），详情显示，供追溯。请求体 `CancelPurchaseOrderRequest` 增加 `refund`（Boolean），有实付时为必填，服务端校验。

6. **待报销项撤回**：新增 `POST /api/v1/finance/reimbursements/items/withdraw`（类型 + 来源 id + 原因 + 幂等键），按类型分派到付款作废 / 费用单作废；校验该项未进有效报销单。权限：本人，或有对应作废权限的财务（全部人员视图）。

7. **取消弹窗预览**：新增只读接口 `GET /api/v1/purchase/orders/{id}/cancel-preview`，返回各笔付款与「会退款」时的处理方式，前端据此展示，避免前端自己推规则。

## Risks / Trade-offs

- [选了「会退款」但店铺最终没退] → 员工垫付已作废时，员工本人垫的钱拿不回来：在弹窗明确提示「作废后不再报销，请确认店铺会原路退给 <垫付人>」；如确实没退，财务可在付款单恢复（作废的付款保留记录，可据此重新登记）。
- [自动登记的已收到退款日期是取消当天，与实际到账日不一致] → 备注写明「自动登记」，垫付人可作废后按实际重登。
- [并发：取消时有人在提交报销单包含同一笔垫付] → 收尾按 `lock` 锁应付单与付款，报销提交也锁付款，二者串行；判定在锁内重新读取 `reimburse_status`。

## Migration Plan

- 迁移 V1.5.51：两张表各加一列，默认 0，存量数据不回填（历史取消的采购订单视为未选）。
- 回退：删除两列，代码回退到上一版即可，无数据依赖。
