package com.zhul.erp.modules.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.modules.finance.constants.FinanceConstants;
import com.zhul.erp.modules.finance.entity.ExpenseDO;
import com.zhul.erp.modules.finance.entity.PayableDO;
import com.zhul.erp.modules.finance.entity.PayableInstallmentDO;
import com.zhul.erp.modules.finance.entity.PaymentAllocationDO;
import com.zhul.erp.modules.finance.repository.ExpenseMapper;
import com.zhul.erp.modules.finance.repository.PayableInstallmentMapper;
import com.zhul.erp.modules.finance.repository.PayableMapper;
import com.zhul.erp.modules.finance.repository.PaymentAllocationMapper;
import com.zhul.erp.modules.finance.support.InstallmentPlanner;
import com.zhul.erp.modules.logistics.entity.ForwarderStatementDO;
import com.zhul.erp.modules.logistics.repository.ForwarderStatementMapper;
import com.zhul.erp.modules.masterdata.entity.SupplierDO;
import com.zhul.erp.modules.masterdata.repository.SupplierMapper;
import com.zhul.erp.modules.masterdata.support.PaymentTerms;
import com.zhul.erp.modules.masterdata.support.SupplierRenamedEvent;
import com.zhul.erp.modules.purchase.purchasereturn.PurchaseReturnConstants;
import com.zhul.erp.modules.purchase.purchasereturn.entity.PurchaseReturnDO;
import com.zhul.erp.modules.purchase.purchasereturn.repository.PurchaseReturnMapper;
import com.zhul.erp.modules.purchase.constants.PurchaseConstants;
import com.zhul.erp.modules.purchase.entity.PurchaseOrderDO;
import com.zhul.erp.modules.purchase.entity.PurchaseOrderItemDO;
import com.zhul.erp.modules.purchase.repository.PurchaseOrderItemMapper;
import com.zhul.erp.modules.purchase.repository.PurchaseOrderMapper;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.system.constants.DocumentType;
import com.zhul.erp.modules.system.service.DocumentNumberService;
import com.zhul.erp.modules.warehouse.constants.WarehouseConstants;
import com.zhul.erp.modules.warehouse.entity.PurchaseReceiptDO;
import com.zhul.erp.modules.warehouse.entity.ReceivingDiscrepancyDO;
import com.zhul.erp.modules.warehouse.repository.PurchaseReceiptMapper;
import com.zhul.erp.modules.warehouse.repository.ReceivingDiscrepancyMapper;
import com.zhul.erp.modules.warehouse.support.ShipProgress;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 应付同步（spec finance/accounts-payable）：按来源单据的当前状态重算应付金额与付款计划，再按核销重算已付、欠款与状态。
 * 都在调用方事务内执行，先锁应付单行；采购订单确认、修改、取消，到货差异处理与重新打开，入库与冲销，运费账单确认时调用。
 */
@Service
@RequiredArgsConstructor
public class PayableSyncService {

    private final PayableMapper payableMapper;
    private final PayableInstallmentMapper installmentMapper;
    private final PaymentAllocationMapper allocationMapper;
    private final PurchaseOrderMapper orderMapper;
    private final PurchaseOrderItemMapper itemMapper;
    private final ReceivingDiscrepancyMapper discrepancyMapper;
    private final PurchaseReceiptMapper receiptMapper;
    private final ShipProgress shipProgress;
    private final SupplierMapper supplierMapper;
    private final ForwarderStatementMapper statementMapper;
    private final ExpenseMapper expenseMapper;
    private final DocumentNumberService documentNumberService;
    private final PurchaseReturnMapper purchaseReturnMapper;
    private final com.zhul.erp.modules.system.doclog.DocLogs docLogs;
    private final com.zhul.erp.modules.inventory.service.InventoryCosting costing;

    /** 采购订单：已下单的生成或重算；取消的关闭（已付转为欠款）；草稿不处理。之后重算库存批次成本（触发原因记为采购订单） */
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void syncPurchaseOrder(Long poId) {
        syncPurchaseOrder(poId, null);
    }

    /**
     * 同上，trigger 为成本调整的触发原因与来源单据（为空时记为「采购订单费用」、来源为该采购订单）。
     * 事务边界：加入调用方事务（没有时自己开一个），应付与批次成本、成本调整单一起提交或回滚（openspec inventory-costing 决策 4）。
     */
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void syncPurchaseOrder(Long poId, com.zhul.erp.modules.inventory.service.InventoryCosting.Trigger trigger) {
        PurchaseOrderDO po = orderMapper.selectById(poId);
        if (po == null || po.getDeletedAt() != null || Objects.equals(po.getStatus(), PurchaseConstants.PO_DRAFT)) {
            return;
        }
        syncPurchaseOrderPayable(po);
        costing.recost(List.of(poId), trigger != null ? trigger : new com.zhul.erp.modules.inventory.service.InventoryCosting.Trigger(
                com.zhul.erp.modules.inventory.constants.InvConstants.CA_PO_FEE, "PO", poId, po.getPoNo()));
    }

    private void syncPurchaseOrderPayable(PurchaseOrderDO po) {
        Long poId = po.getId();
        boolean cancelled = Objects.equals(po.getStatus(), PurchaseConstants.PO_CANCELLED);
        PayableDO p = lockBySource(FinanceConstants.SOURCE_PURCHASE_ORDER, poId);
        boolean created = p == null;
        if (created) {
            if (cancelled) {
                return;
            }
            p = newPayable(FinanceConstants.SOURCE_PURCHASE_ORDER, poId, po.getPoNo(), po.getCurrencyCode(), po.getExchangeRate());
            fillPayee(p, po);
            payableMapper.insert(p);
        }
        BigDecimal amount = cancelled ? BigDecimal.ZERO : nz(po.getTotalAmount()).subtract(discountOf(poId))
                .subtract(returnCreditOf(poId)).max(BigDecimal.ZERO);
        InstallmentPlanner.Dates dates = new InstallmentPlanner.Dates(po.getOrderDate(), po.getExpectedShipDate(),
                receivedDate(po), null);
        List<InstallmentPlanner.Term> terms = PaymentTerms.fromJson(po.getPaymentTerms()).stream()
                .map(t -> new InstallmentPlanner.Term(t.getPercent(), t.getTrigger(), t.getDays() == null ? 0 : t.getDays()))
                .toList();
        p.setSourceNo(po.getPoNo());
        // 线上店铺转为供应商后，收款方随之更新
        fillPayee(p, po);
        // 币种随采购订单（有资金往来时采购订单不允许改币种，见 hasMoneyMovement；audit BUS-004）
        p.setCurrencyCode(po.getCurrencyCode());
        p.setExchangeRate(po.getExchangeRate() == null ? BigDecimal.ONE : po.getExchangeRate());
        apply(p, amount, InstallmentPlanner.plan(amount, terms, dates), cancelled, created);
    }

    /** 来源单据的应付是否已有资金往来：有效的付款、退款核销，或作为欠款来源被抵扣（作废的付款核销已软删除，不算） */
    public boolean hasMoneyMovement(int sourceType, Long sourceId) {
        PayableDO p = lockBySource(sourceType, sourceId);
        return p != null && allocationMapper.selectCount(new LambdaQueryWrapper<PaymentAllocationDO>()
                .isNull(PaymentAllocationDO::getDeletedAt)
                .and(w -> w.eq(PaymentAllocationDO::getPayableId, p.getId())
                        .or().eq(PaymentAllocationDO::getCreditPayableId, p.getId()))) > 0;
    }

    /** 运费账单确认：生成一期应付，确认当天到期；已有的不再生成 */
    public void createForStatement(Long statementId) {
        ForwarderStatementDO st = statementMapper.selectById(statementId);
        if (st == null || st.getDeletedAt() != null) {
            return;
        }
        PayableDO p = lockBySource(FinanceConstants.SOURCE_STATEMENT, statementId);
        if (p != null) {
            return;
        }
        p = newPayable(FinanceConstants.SOURCE_STATEMENT, statementId, "运费账单 " + st.getPeriod(), "CNY", BigDecimal.ONE);
        p.setPayeeType(FinanceConstants.PAYEE_FORWARDER);
        p.setSupplierId(st.getForwarderId());
        p.setPayeeName(supplierName(st.getForwarderId()));
        payableMapper.insert(p);
        BigDecimal amount = nz(st.getStatementTotal());
        LocalDate due = st.getConfirmedAt() == null ? LocalDate.now() : st.getConfirmedAt().toLocalDate();
        apply(p, amount, InstallmentPlanner.single(amount, due), false, true);
    }

    /**
     * 费用单（③b）：公司对公、私对私且有效的生成或重算一期应付，到期日为发生日期；作废或改为员工垫付的关闭已有应付。
     * 私对私的应付在保存时由同一事务登记的私对私付款付清。
     */
    public void syncExpense(Long expenseId) {
        ExpenseDO e = expenseMapper.selectById(expenseId);
        if (e == null || e.getDeletedAt() != null) {
            return;
        }
        boolean active = Objects.equals(e.getStatus(), FinanceConstants.FE_VALID)
                && (Objects.equals(e.getMethod(), FinanceConstants.METHOD_COMPANY)
                || Objects.equals(e.getMethod(), FinanceConstants.METHOD_PRIVATE));
        PayableDO p = lockBySource(FinanceConstants.SOURCE_EXPENSE, expenseId);
        boolean created = p == null;
        if (created) {
            if (!active) {
                return;
            }
            p = newPayable(FinanceConstants.SOURCE_EXPENSE, expenseId, e.getExpenseNo(), e.getCurrencyCode(), e.getExchangeRate());
            payableMapper.insert(p);
        }
        p.setPayeeType(e.getPayeeType());
        p.setSupplierId(e.getSupplierId());
        p.setShopPlatform(e.getShopPlatform() == null ? "" : e.getShopPlatform());
        p.setShopName(e.getShopName() == null ? "" : e.getShopName());
        p.setPayeeName(e.getPayeeName());
        p.setCurrencyCode(e.getCurrencyCode());
        p.setExchangeRate(e.getExchangeRate());
        BigDecimal amount = active ? e.getAmount() : BigDecimal.ZERO;
        apply(p, amount, InstallmentPlanner.single(amount, e.getOccurDate()), !active, created);
    }

    /** 付款登记、作废、退款后：锁住应付单，按现有付款计划重算已付、欠款与状态 */
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void refresh(Long payableId) {
        refresh(payableId, null);
    }

    /**
     * 同上；采购订单的应付已付变了，外币实际汇率随之变化，按 trigger 重算该采购订单下的库存批次成本（为空时不重算）。
     * 事务边界：调用方（付款服务）事务内。
     */
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void refresh(Long payableId, com.zhul.erp.modules.inventory.service.InventoryCosting.Trigger trigger) {
        PayableDO p = payableMapper.selectOne(new LambdaQueryWrapper<PayableDO>()
                .eq(PayableDO::getId, payableId).last("FOR UPDATE"));
        if (p == null) {
            return;
        }
        List<InstallmentPlanner.Inst> plan = installmentsOf(payableId).stream()
                .map(i -> new InstallmentPlanner.Inst(i.getSeq(), i.getRatio(), i.getTriggerType(), i.getOffsetDays(),
                        i.getAmount(), i.getDueDate(), BigDecimal.ZERO, FinanceConstants.STATUS_UNPAID))
                .toList();
        apply(p, nz(p.getAmount()), plan, Objects.equals(p.getStatus(), FinanceConstants.STATUS_CLOSED), false);
        if (trigger != null && Objects.equals(p.getSourceType(), FinanceConstants.SOURCE_PURCHASE_ORDER) && p.getSourceId() != null) {
            costing.recost(List.of(p.getSourceId()), trigger);
        }
    }

    /** 已付合计、欠款、状态、各期已付与下一期到期日，写回应付单与付款计划 */
    private void apply(PayableDO p, BigDecimal amount, List<InstallmentPlanner.Inst> plan, boolean closed, boolean created) {
        BigDecimal before = nz(p.getAmount());
        boolean wasClosed = Objects.equals(p.getStatus(), FinanceConstants.STATUS_CLOSED);
        BigDecimal rate = p.getExchangeRate() == null ? BigDecimal.ONE : p.getExchangeRate();
        BigDecimal amountCny = amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        // 历史迁移视为已付：迁移前已线下付清，没有付款核销，已付跟着应付走
        boolean settled = Objects.equals(p.getSettledByMigration(), 1);
        Totals t = settled ? new Totals(amount, amountCny) : totals(p.getId());
        BigDecimal paid = t.paid();
        List<InstallmentPlanner.Inst> filled = InstallmentPlanner.allocate(plan, paid.min(amount));
        p.setAmount(amount);
        p.setAmountCny(amountCny);
        p.setPaidAmount(paid);
        p.setPaidCny(t.paidCny());
        p.setCreditAmount(paid.subtract(amount).max(BigDecimal.ZERO));
        if (p.getCreditAmount().signum() == 0) {
            // 欠款退完或抵扣用完，「待退款」标记随之去掉（openspec po-cancel-refund）
            p.setRefundExpectedAt(null);
        }
        p.setStatus(closed ? FinanceConstants.STATUS_CLOSED
                : paid.signum() > 0 && paid.compareTo(amount) >= 0 ? FinanceConstants.STATUS_PAID
                : paid.signum() > 0 ? FinanceConstants.STATUS_PARTIAL
                : amount.signum() == 0 ? FinanceConstants.STATUS_PAID : FinanceConstants.STATUS_UNPAID);
        p.setNextDueDate(closed ? null : InstallmentPlanner.nextDue(filled));
        payableMapper.updateById(p);
        saveInstallments(p, filled);
        logChange(p, created, before, wasClosed, closed);
    }

    /** 应付单操作日志（change finance-ap-fx-docs）：生成、应付金额变化、关闭与重新打开；付款引起的变化由付款一侧写 */
    private void logChange(PayableDO p, boolean created, BigDecimal before, boolean wasClosed, boolean closed) {
        String cur = p.getCurrencyCode() + " ";
        if (created) {
            docLogs.add(DocumentType.AP, p.getId(), "生成", "来源单号 " + p.getSourceNo() + "，应付 " + cur + p.getAmount().toPlainString());
            return;
        }
        if (before.compareTo(p.getAmount()) != 0) {
            docLogs.add(DocumentType.AP, p.getId(), "应付金额变化", cur + before.toPlainString() + " → " + cur
                    + p.getAmount().toPlainString() + "（" + p.getSourceNo() + "）");
        }
        if (closed && !wasClosed) {
            docLogs.add(DocumentType.AP, p.getId(), "关闭", "来源单据已取消或作废");
        } else if (!closed && wasClosed) {
            docLogs.add(DocumentType.AP, p.getId(), "重新打开", "");
        }
    }

    /** 付款计划按期次更新；期数变少时多出的软删除 */
    private void saveInstallments(PayableDO p, List<InstallmentPlanner.Inst> filled) {
        Map<Integer, PayableInstallmentDO> existing = new java.util.HashMap<>();
        installmentsOf(p.getId()).forEach(i -> existing.put(i.getSeq(), i));
        for (InstallmentPlanner.Inst f : filled) {
            PayableInstallmentDO row = existing.remove(f.seq());
            boolean create = row == null;
            if (create) {
                row = new PayableInstallmentDO();
                row.setTenantId(p.getTenantId());
                row.setPayableId(p.getId());
                row.setSeq(f.seq());
            }
            row.setRatio(f.ratio());
            row.setTriggerType(f.trigger());
            row.setOffsetDays(f.days());
            row.setAmount(f.amount());
            row.setDueDate(f.dueDate());
            row.setPaidAmount(f.paid());
            row.setStatus(f.status());
            if (create) {
                installmentMapper.insert(row);
            } else {
                installmentMapper.updateById(row);
            }
        }
        LocalDateTime now = LocalDateTime.now();
        for (PayableInstallmentDO extra : existing.values()) {
            extra.setDeletedAt(now);
            installmentMapper.updateById(extra);
        }
    }

    public List<PayableInstallmentDO> installmentsOf(Long payableId) {
        return installmentMapper.selectList(new LambdaQueryWrapper<PayableInstallmentDO>()
                .eq(PayableInstallmentDO::getPayableId, payableId)
                .isNull(PayableInstallmentDO::getDeletedAt)
                .orderByAsc(PayableInstallmentDO::getSeq));
    }

    /**
     * 净已付 = 核销进来的（实付 + 欠款抵扣）− 抵扣出去的 − 退款冲减。
     * 已付人民币 = 实付 − 退款，加上抵扣带来的成本人民币：抵扣原币 × 来源应付的实付汇率，转入目标、来源相应减少
     * （audit BUS-019，成本口径；抵扣行的现金流 amount_cny 仍为 0）。
     */
    public record Totals(BigDecimal paid, BigDecimal paidCny) {
    }

    public Totals totals(Long payableId) {
        BigDecimal paid = BigDecimal.ZERO;
        BigDecimal cny = BigDecimal.ZERO;
        Map<Long, BigDecimal[]> cashOf = new java.util.HashMap<>();
        for (PaymentAllocationDO a : allocationMapper.selectList(new LambdaQueryWrapper<PaymentAllocationDO>()
                .and(w -> w.eq(PaymentAllocationDO::getPayableId, payableId).or()
                        .eq(PaymentAllocationDO::getCreditPayableId, payableId))
                .isNull(PaymentAllocationDO::getDeletedAt))) {
            boolean into = payableId.equals(a.getPayableId());
            int kind = a.getFromCredit() == null ? 0 : a.getFromCredit();
            if (into && kind == 0) {
                paid = paid.add(a.getAmount());
                cny = cny.add(nz(a.getAmountCny()));
            } else if (into && kind == 1) {
                paid = paid.add(a.getAmount());
                cny = cny.add(creditCny(a, cashOf));
            } else if (into && kind == 2) {
                paid = paid.subtract(a.getAmount());
                cny = cny.subtract(nz(a.getAmountCny()));
            }
            if (kind == 1 && payableId.equals(a.getCreditPayableId())) {
                paid = paid.subtract(a.getAmount());
                cny = cny.subtract(creditCny(a, cashOf));
            }
        }
        return new Totals(paid, cny);
    }

    /**
     * 抵扣的成本人民币：抵扣原币 × 来源实付人民币 ÷ 来源实付原币（只算实付行，不舍入中间汇率），结果四舍五入到 2 位（HALF_UP）；
     * 来源没有实付时按来源应付的汇率
     */
    private BigDecimal creditCny(PaymentAllocationDO a, Map<Long, BigDecimal[]> cashOf) {
        BigDecimal[] cash = cashOf.computeIfAbsent(a.getCreditPayableId(), id -> {
            BigDecimal amt = BigDecimal.ZERO;
            BigDecimal amtCny = BigDecimal.ZERO;
            for (PaymentAllocationDO c : allocationMapper.selectList(new LambdaQueryWrapper<PaymentAllocationDO>()
                    .eq(PaymentAllocationDO::getPayableId, id)
                    .eq(PaymentAllocationDO::getFromCredit, 0)
                    .isNull(PaymentAllocationDO::getDeletedAt))) {
                amt = amt.add(c.getAmount());
                amtCny = amtCny.add(nz(c.getAmountCny()));
            }
            if (amt.signum() > 0) {
                return new BigDecimal[] {amt, amtCny};
            }
            PayableDO source = payableMapper.selectById(id);
            BigDecimal rate = source == null || source.getExchangeRate() == null ? BigDecimal.ONE : source.getExchangeRate();
            return new BigDecimal[] {BigDecimal.ONE, rate};
        });
        return a.getAmount().multiply(cash[1]).divide(cash[0], 2, RoundingMode.HALF_UP);
    }

    public PayableDO lockBySource(int sourceType, Long sourceId) {
        return payableMapper.selectOne(new LambdaQueryWrapper<PayableDO>()
                .eq(PayableDO::getTenantId, PiStore.tenantId())
                .eq(PayableDO::getSourceType, sourceType)
                .eq(PayableDO::getSourceId, sourceId)
                .last("FOR UPDATE"));
    }

    private PayableDO newPayable(int sourceType, Long sourceId, String sourceNo, String currency, BigDecimal rate) {
        PayableDO p = new PayableDO();
        p.setTenantId(PiStore.tenantId());
        p.setPayableNo(documentNumberService.next(DocumentType.AP));
        p.setSourceType(sourceType);
        p.setSourceId(sourceId);
        p.setSourceNo(sourceNo == null ? "" : sourceNo);
        p.setCurrencyCode(currency == null ? "CNY" : currency);
        p.setExchangeRate(rate == null ? BigDecimal.ONE : rate);
        p.setAmount(BigDecimal.ZERO);
        p.setAmountCny(BigDecimal.ZERO);
        p.setPaidAmount(BigDecimal.ZERO);
        p.setPaidCny(BigDecimal.ZERO);
        p.setCreditAmount(BigDecimal.ZERO);
        p.setStatus(FinanceConstants.STATUS_UNPAID);
        p.setShopPlatform("");
        p.setShopName("");
        return p;
    }

    private void fillPayee(PayableDO p, PurchaseOrderDO po) {
        boolean shop = po.getChannel() != null && po.getChannel() != PurchaseConstants.CHANNEL_SUPPLIER;
        if (shop) {
            p.setPayeeType(FinanceConstants.PAYEE_SHOP);
            p.setShopPlatform(String.valueOf(po.getChannel()));
            p.setShopName(po.getShopName() == null ? "" : po.getShopName());
            p.setPayeeName(PurchaseConstants.CHANNEL_NAMES.get(po.getChannel()) + " · " + p.getShopName());
        } else {
            p.setPayeeType(FinanceConstants.PAYEE_SUPPLIER);
            p.setSupplierId(po.getSupplierId());
            p.setPayeeName(supplierName(po.getSupplierId()));
        }
    }

    private String supplierName(Long id) {
        SupplierDO s = id == null ? null : supplierMapper.selectById(id);
        return s == null ? "" : SupplierRenamedEvent.displayName(s);
    }

    /** 供应商改名：引用它的应付（供应商、货代）收款方跟着改，与改名同一事务 */
    @EventListener
    public void onSupplierRenamed(SupplierRenamedEvent e) {
        payableMapper.update(null, new LambdaUpdateWrapper<PayableDO>()
                .eq(PayableDO::getTenantId, e.tenantId())
                .eq(PayableDO::getSupplierId, e.supplierId())
                .in(PayableDO::getPayeeType, FinanceConstants.PAYEE_SUPPLIER, FinanceConstants.PAYEE_FORWARDER)
                .isNull(PayableDO::getDeletedAt)
                .set(PayableDO::getPayeeName, e.displayName())
                .set(PayableDO::getUpdateTime, LocalDateTime.now()));
    }

    /** 已处理的「折价接收」差异的折价合计 */
    private BigDecimal discountOf(Long poId) {
        return discrepancyMapper.selectList(new LambdaQueryWrapper<ReceivingDiscrepancyDO>()
                        .select(ReceivingDiscrepancyDO::getDiscountAmount)
                        .eq(ReceivingDiscrepancyDO::getPoId, poId)
                        .eq(ReceivingDiscrepancyDO::getStatus, WarehouseConstants.DIFF_HANDLED)
                        .eq(ReceivingDiscrepancyDO::getResolution, WarehouseConstants.RES_DISCOUNT)
                        .isNull(ReceivingDiscrepancyDO::getDeletedAt))
                .stream().map(d -> nz(d.getDiscountAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 已完成的售后来源、库存退回采购退货单的退款或抵扣合计（采购订单币种；历史迁移的不冲减，spec finance/accounts-payable「应付金额同步」） */
    private BigDecimal returnCreditOf(Long poId) {
        return purchaseReturnMapper.selectList(new LambdaQueryWrapper<PurchaseReturnDO>()
                        .select(PurchaseReturnDO::getRefundAmount)
                        .eq(PurchaseReturnDO::getPoId, poId)
                        .in(PurchaseReturnDO::getSourceType,
                                PurchaseReturnConstants.SRC_AFTER_SALES, PurchaseReturnConstants.SRC_STOCK)
                        .eq(PurchaseReturnDO::getStatus,
                                PurchaseReturnConstants.DONE)
                        .eq(PurchaseReturnDO::getMigrated, 0)
                        .isNull(PurchaseReturnDO::getDeletedAt))
                .stream().map(r -> nz(r.getRefundAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 全部入库（发货进度为已入库）时取有效采购入库单最晚的入库日期，否则为空 */
    private LocalDate receivedDate(PurchaseOrderDO po) {
        if (!Objects.equals(po.getStatus(), PurchaseConstants.PO_ORDERED)) {
            return null;
        }
        List<PurchaseOrderItemDO> lines = itemMapper.selectList(new LambdaQueryWrapper<PurchaseOrderItemDO>()
                .eq(PurchaseOrderItemDO::getPoId, po.getId())
                .isNull(PurchaseOrderItemDO::getDeletedAt));
        if (lines.isEmpty()) {
            return null;
        }
        ShipProgress.Result r = shipProgress.of(Map.of(po.getId(), lines)).get(po.getId());
        if (r == null || !ShipProgress.RECEIVED.equals(r.code())) {
            return null;
        }
        return receiptMapper.selectList(new LambdaQueryWrapper<PurchaseReceiptDO>()
                        .select(PurchaseReceiptDO::getReceivedDate)
                        .eq(PurchaseReceiptDO::getPoId, po.getId())
                        .eq(PurchaseReceiptDO::getStatus, WarehouseConstants.GR_VALID)
                        .isNull(PurchaseReceiptDO::getDeletedAt))
                .stream().map(PurchaseReceiptDO::getReceivedDate).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(LocalDate.now());
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
