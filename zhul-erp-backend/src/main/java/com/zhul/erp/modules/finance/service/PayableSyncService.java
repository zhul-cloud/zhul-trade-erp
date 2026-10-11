package com.zhul.erp.modules.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
 * 都在调用方事务内执行，先锁应付单行；采购单确认、修改、取消，到货差异处理与重新打开，入库与冲销，货代对账确认时调用。
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

    /** 采购单：已下单的生成或重算；取消的关闭（已付转为欠款）；草稿不处理 */
    public void syncPurchaseOrder(Long poId) {
        PurchaseOrderDO po = orderMapper.selectById(poId);
        if (po == null || po.getDeletedAt() != null || Objects.equals(po.getStatus(), PurchaseConstants.PO_DRAFT)) {
            return;
        }
        boolean cancelled = Objects.equals(po.getStatus(), PurchaseConstants.PO_CANCELLED);
        PayableDO p = lockBySource(FinanceConstants.SOURCE_PURCHASE_ORDER, poId);
        if (p == null) {
            if (cancelled) {
                return;
            }
            p = newPayable(FinanceConstants.SOURCE_PURCHASE_ORDER, poId, po.getPoNo(), po.getCurrencyCode(), po.getExchangeRate());
            fillPayee(p, po);
            payableMapper.insert(p);
        }
        BigDecimal amount = cancelled ? BigDecimal.ZERO : nz(po.getTotalAmount()).subtract(discountOf(poId)).max(BigDecimal.ZERO);
        InstallmentPlanner.Dates dates = new InstallmentPlanner.Dates(po.getOrderDate(), po.getExpectedShipDate(),
                receivedDate(po), null);
        List<InstallmentPlanner.Term> terms = PaymentTerms.fromJson(po.getPaymentTerms()).stream()
                .map(t -> new InstallmentPlanner.Term(t.getPercent(), t.getTrigger(), t.getDays() == null ? 0 : t.getDays()))
                .toList();
        p.setSourceNo(po.getPoNo());
        // 线上店铺转为供应商后，收款方随之更新
        fillPayee(p, po);
        p.setExchangeRate(po.getExchangeRate() == null ? BigDecimal.ONE : po.getExchangeRate());
        apply(p, amount, InstallmentPlanner.plan(amount, terms, dates), cancelled);
    }

    /** 货代对账单确认：生成一期应付，确认当天到期；已有的不再生成 */
    public void createForStatement(Long statementId) {
        ForwarderStatementDO st = statementMapper.selectById(statementId);
        if (st == null || st.getDeletedAt() != null) {
            return;
        }
        PayableDO p = lockBySource(FinanceConstants.SOURCE_STATEMENT, statementId);
        if (p != null) {
            return;
        }
        p = newPayable(FinanceConstants.SOURCE_STATEMENT, statementId, "货代对账 " + st.getPeriod(), "CNY", BigDecimal.ONE);
        p.setPayeeType(FinanceConstants.PAYEE_FORWARDER);
        p.setSupplierId(st.getForwarderId());
        p.setPayeeName(supplierName(st.getForwarderId()));
        payableMapper.insert(p);
        BigDecimal amount = nz(st.getStatementTotal());
        LocalDate due = st.getConfirmedAt() == null ? LocalDate.now() : st.getConfirmedAt().toLocalDate();
        apply(p, amount, InstallmentPlanner.single(amount, due), false);
    }

    /**
     * 费用单（③b）：公司对公且有效的生成或重算一期应付，到期日为发生日期；作废或改为员工垫付的关闭已有应付。
     */
    public void syncExpense(Long expenseId) {
        ExpenseDO e = expenseMapper.selectById(expenseId);
        if (e == null || e.getDeletedAt() != null) {
            return;
        }
        boolean active = Objects.equals(e.getStatus(), FinanceConstants.FE_VALID)
                && Objects.equals(e.getMethod(), FinanceConstants.METHOD_COMPANY);
        PayableDO p = lockBySource(FinanceConstants.SOURCE_EXPENSE, expenseId);
        if (p == null) {
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
        apply(p, amount, InstallmentPlanner.single(amount, e.getOccurDate()), !active);
    }

    /** 付款登记、作废、退款后：锁住应付单，按现有付款计划重算已付、欠款与状态 */
    public void refresh(Long payableId) {
        PayableDO p = payableMapper.selectOne(new LambdaQueryWrapper<PayableDO>()
                .eq(PayableDO::getId, payableId).last("FOR UPDATE"));
        if (p == null) {
            return;
        }
        List<InstallmentPlanner.Inst> plan = installmentsOf(payableId).stream()
                .map(i -> new InstallmentPlanner.Inst(i.getSeq(), i.getRatio(), i.getTriggerType(), i.getOffsetDays(),
                        i.getAmount(), i.getDueDate(), BigDecimal.ZERO, FinanceConstants.STATUS_UNPAID))
                .toList();
        apply(p, nz(p.getAmount()), plan, Objects.equals(p.getStatus(), FinanceConstants.STATUS_CLOSED));
    }

    /** 已付合计、欠款、状态、各期已付与下一期到期日，写回应付单与付款计划 */
    private void apply(PayableDO p, BigDecimal amount, List<InstallmentPlanner.Inst> plan, boolean closed) {
        Totals t = totals(p.getId());
        BigDecimal paid = t.paid();
        List<InstallmentPlanner.Inst> filled = InstallmentPlanner.allocate(plan, paid.min(amount));
        p.setAmount(amount);
        BigDecimal rate = p.getExchangeRate() == null ? BigDecimal.ONE : p.getExchangeRate();
        p.setAmountCny(amount.multiply(rate).setScale(2, RoundingMode.HALF_UP));
        p.setPaidAmount(paid);
        p.setPaidCny(t.paidCny());
        p.setCreditAmount(paid.subtract(amount).max(BigDecimal.ZERO));
        p.setStatus(closed ? FinanceConstants.STATUS_CLOSED
                : paid.signum() > 0 && paid.compareTo(amount) >= 0 ? FinanceConstants.STATUS_PAID
                : paid.signum() > 0 ? FinanceConstants.STATUS_PARTIAL
                : amount.signum() == 0 ? FinanceConstants.STATUS_PAID : FinanceConstants.STATUS_UNPAID);
        p.setNextDueDate(closed ? null : InstallmentPlanner.nextDue(filled));
        payableMapper.updateById(p);
        saveInstallments(p, filled);
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

    /** 净已付 = 核销进来的（实付 + 欠款抵扣）− 抵扣出去的 − 退款冲减；已付人民币只算实付减退款 */
    public record Totals(BigDecimal paid, BigDecimal paidCny) {
    }

    public Totals totals(Long payableId) {
        BigDecimal paid = BigDecimal.ZERO;
        BigDecimal cny = BigDecimal.ZERO;
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
            } else if (into && kind == 2) {
                paid = paid.subtract(a.getAmount());
                cny = cny.subtract(nz(a.getAmountCny()));
            }
            if (kind == 1 && payableId.equals(a.getCreditPayableId())) {
                paid = paid.subtract(a.getAmount());
            }
        }
        return new Totals(paid, cny);
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
        if (s == null) {
            return "";
        }
        String name = s.getShortName() != null && !s.getShortName().isBlank() ? s.getShortName() : s.getName();
        return name.length() > 128 ? name.substring(0, 128) : name;
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

    /** 全部入库（发货进度为已入库）时取有效入库单最晚的入库日期，否则为空 */
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
