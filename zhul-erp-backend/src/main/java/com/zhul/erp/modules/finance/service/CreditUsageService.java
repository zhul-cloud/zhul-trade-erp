package com.zhul.erp.modules.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.finance.constants.FinanceConstants;
import com.zhul.erp.modules.finance.entity.CustomerCreditConsumptionDO;
import com.zhul.erp.modules.finance.entity.CustomerCreditEntryDO;
import com.zhul.erp.modules.finance.repository.CustomerCreditConsumptionMapper;
import com.zhul.erp.modules.finance.repository.CustomerCreditEntryMapper;
import com.zhul.erp.modules.finance.service.CustomerCreditLedger.Account;
import com.zhul.erp.modules.finance.service.CustomerCreditLedger.Source;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.sales.constants.SalesConstants;
import com.zhul.erp.modules.sales.entity.PaymentReceiptDO;
import com.zhul.erp.modules.sales.entity.PiVersionDO;
import com.zhul.erp.modules.sales.entity.ProformaInvoiceDO;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.repository.PaymentReceiptMapper;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.sales.support.PiSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * 用客户余额付款（spec sales/proforma-invoice「PI 抵扣客户余额」、sales/payment-receipt「手动订单抵扣客户余额」）：
 * <ul>
 *   <li>PI：占用金额 = max(已发送版本的抵扣, 编辑中版本的抵扣)，保存草稿即占用；关闭、作废、删除时为 0（释放）；
 *       已发送（含已转订单）时按已发送版本的抵扣生成一条付款方式为「余额抵扣」的收款记录，计入已到账；</li>
 *   <li>手动订单：抵扣即占用并生成收款记录；作废这条记录或取消订单时释放。</li>
 * </ul>
 * 收款记录的人民币取占用流水锁定的价值（按金额比例），不参与结汇（settle_status 为不适用）。
 * 事务边界：与 PI 保存、发送、关闭、作废等操作在同一事务内（调用方声明）。
 */
@Service
@RequiredArgsConstructor
public class CreditUsageService {

    private static final MathContext MC = MathContext.DECIMAL128;

    private final CustomerCreditLedger ledger;
    private final com.zhul.erp.modules.sales.support.ReceiptDocs receiptDocs;
    private final CustomerCreditEntryMapper entryMapper;
    private final CustomerCreditConsumptionMapper consumptionMapper;
    private final PaymentReceiptMapper receiptMapper;
    private final PiStore store;
    private final PiSummary summary;
    private final CurrentUserResolver currentUser;
    private final com.zhul.erp.modules.sales.support.ReceiptPosition receiptPosition;
    private final CreditReceiptSync receiptSync;

    /** PI 状态或抵扣金额变化后调用：按目标金额重新占用，并同步余额抵扣收款记录 */
    @Transactional(rollbackFor = Exception.class)
    public void syncPi(ProformaInvoiceDO pi) {
        boolean closed = pi.getDeletedAt() != null || pi.getStatus() == SalesConstants.PI_VOID || pi.getStatus() == SalesConstants.PI_CLOSED;
        BigDecimal sent = credit(store.version(pi.getId(), pi.getCurrentVersionNo() != null && pi.getCurrentVersionNo() > 0
                ? pi.getCurrentVersionNo() : null));
        BigDecimal editing = credit(store.version(pi.getId(), pi.getEditingVersionNo()));
        BigDecimal target = closed ? BigDecimal.ZERO : sent.max(editing);
        Source src = new Source(FinanceConstants.SRC_PI, pi.getId(), pi.getPiNo());
        CustomerCreditEntryDO occupation = reoccupy(new Account(pi.getCustomerId(), pi.getCurrencyCode(), pi.getBusinessUnitId()),
                src, target, closed ? "PI " + statusName(pi) + "，释放余额" : "PI 抵扣");
        boolean counts = !closed && (pi.getStatus() == SalesConstants.PI_SENT || pi.getStatus() == SalesConstants.PI_CONVERTED);
        if (syncReceipt(new LambdaQueryWrapper<PaymentReceiptDO>().eq(PaymentReceiptDO::getPiId, pi.getId()),
                counts ? sent : BigDecimal.ZERO, occupation, pi, null)) {
            summary.refresh(pi);
        }
    }

    /** 手动订单抵扣客户余额：生成「余额抵扣」收款记录，计入已到账；调用方已确认是进行中的手动订单 */
    @Transactional(rollbackFor = Exception.class)
    public void useOnOrder(SalesOrderDO o, BigDecimal amount) {
        BigDecimal amt = amount == null ? BigDecimal.ZERO : amount.setScale(2, RoundingMode.HALF_UP);
        if (amt.signum() <= 0) {
            throw new BizException("抵扣金额须大于 0");
        }
        BigDecimal unpaid = o.getTotalAmount().subtract(o.getReceivedAmount()).subtract(o.getFeeDiffAmount());
        if (amt.compareTo(unpaid) > 0) {
            throw new BizException("抵扣不能超过待付金额 " + o.getCurrencyCode() + " " + CustomerCreditLedger.fmt(unpaid.max(BigDecimal.ZERO)));
        }
        CustomerCreditEntryDO out = ledger.consume(new Account(o.getCustomerId(), o.getCurrencyCode(), o.getBusinessUnitId()),
                FinanceConstants.CREDIT_OCCUPY, amt, new Source(FinanceConstants.SRC_SO, o.getId(), o.getSoNo()), "手动订单抵扣");
        insertReceipt(amt, out.getAmountCny().negate(), out, o.getTenantId(), o.getCurrencyCode(), o.getBusinessUnitId(), null, o.getId());
        summary.refreshOrder(o);
    }

    /** 作废手动订单上的余额抵扣（登记人或有「登记到账」权限的用户），释放余额；PI 上的抵扣由调用方挡住 */
    @Transactional(rollbackFor = Exception.class)
    public void voidOrderCredit(SalesOrderDO o, PaymentReceiptDO r, String reason) {
        voidReceipt(r, reason);
        if (r.getCreditEntryId() != null) {
            ledger.release(ledger.require(r.getCreditEntryId()), FinanceConstants.CREDIT_RELEASE, "作废订单 " + o.getSoNo() + " 的余额抵扣");
        }
        summary.refreshOrder(o);
    }

    /** 手动订单取消：作废全部余额抵扣并释放 */
    @Transactional(rollbackFor = Exception.class)
    public void releaseOrder(SalesOrderDO o) {
        List<PaymentReceiptDO> rows = receiptMapper.selectList(valid().eq(PaymentReceiptDO::getSoId, o.getId()));
        for (PaymentReceiptDO r : rows) {
            voidReceipt(r, "订单已取消");
            if (r.getCreditEntryId() != null) {
                ledger.release(ledger.require(r.getCreditEntryId()), FinanceConstants.CREDIT_RELEASE, "订单 " + o.getSoNo() + " 取消，释放余额");
            }
        }
        if (!rows.isEmpty()) {
            summary.refreshOrder(o);
        }
    }

    /** PI 的余额面板 */
    public com.zhul.erp.modules.sales.dto.CreditPanelVO panel(ProformaInvoiceDO pi) {
        return panel(receiptPosition.ofPi(pi), new Source(FinanceConstants.SRC_PI, pi.getId(), pi.getPiNo()));
    }

    /** 订单的余额面板（PI 转成的订单取 PI 的） */
    public com.zhul.erp.modules.sales.dto.CreditPanelVO panel(SalesOrderDO o) {
        com.zhul.erp.modules.sales.support.ReceiptPosition.Position p = receiptPosition.ofOrder(o);
        return panel(p, new Source(p.ownerType(), p.ownerId(), p.ownerNo()));
    }

    private com.zhul.erp.modules.sales.dto.CreditPanelVO panel(com.zhul.erp.modules.sales.support.ReceiptPosition.Position p, Source src) {
        com.zhul.erp.modules.sales.dto.CreditPanelVO v = new com.zhul.erp.modules.sales.dto.CreditPanelVO();
        Account a = new Account(p.customerId(), p.currencyCode(), p.businessUnitId());
        v.setAvailable(ledger.balance(a).amount());
        CustomerCreditEntryDO active = activeOccupation(src);
        v.setOccupiedHere(active == null ? BigDecimal.ZERO : unreleased(active));
        BigDecimal all = entryMapper.selectList(new LambdaQueryWrapper<CustomerCreditEntryDO>()
                        .eq(CustomerCreditEntryDO::getTenantId, PiStore.tenantId())
                        .eq(CustomerCreditEntryDO::getCustomerId, p.customerId())
                        .eq(CustomerCreditEntryDO::getCurrencyCode, p.currencyCode())
                        .isNull(CustomerCreditEntryDO::getDeletedAt))
                .stream().map(CustomerCreditEntryDO::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        v.setOtherUnits(all.subtract(v.getAvailable()));
        v.setCreditReceived(p.creditReceived());
        v.setCollection(p.collection());
        v.setCollectionFee(p.collectionFee());
        v.setCollectionNet(p.collectionNet());
        List<CustomerCreditEntryDO> transferred = receiptSync.active(p, FinanceConstants.CREDIT_COLLECTION_IN);
        v.setCollectionTransferred(!transferred.isEmpty());
        v.setCollectionValue(transferred.stream().map(CustomerCreditEntryDO::getAmountCny).reduce(BigDecimal.ZERO, BigDecimal::add));
        v.setOverpaid(p.overpaid());
        v.setOverpaidPending(receiptSync.pendingOverpaid(p));
        return v;
    }

    // ---------------------------------------------------------------- 内部

    /** 把来源单据的占用调整到 target：金额不同时释放原占用再按新金额占用（同一批次先进先出，价值不变） */
    private CustomerCreditEntryDO reoccupy(Account a, Source src, BigDecimal target, String remark) {
        CustomerCreditEntryDO active = activeOccupation(src);
        BigDecimal current = active == null ? BigDecimal.ZERO : unreleased(active);
        if (current.compareTo(target) == 0) {
            return active;
        }
        if (active != null) {
            ledger.release(active, FinanceConstants.CREDIT_RELEASE, remark);
        }
        return target.signum() > 0 ? ledger.consume(a, FinanceConstants.CREDIT_OCCUPY, target, src, remark) : null;
    }

    /** 把有效的余额抵扣收款记录调整到 target 金额；返回是否有变化 */
    private boolean syncReceipt(LambdaQueryWrapper<PaymentReceiptDO> owner, BigDecimal target, CustomerCreditEntryDO occupation,
                                ProformaInvoiceDO pi, Long soId) {
        List<PaymentReceiptDO> existing = receiptMapper.selectList(owner.eq(PaymentReceiptDO::getKind, SalesConstants.KIND_RECEIPT)
                .eq(PaymentReceiptDO::getPaymentMethod, SalesConstants.METHOD_CREDIT)
                .eq(PaymentReceiptDO::getStatus, SalesConstants.RECORD_VALID)
                .isNull(PaymentReceiptDO::getDeletedAt));
        BigDecimal current = existing.stream().map(PaymentReceiptDO::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean sameEntry = occupation != null && existing.stream().allMatch(r -> Objects.equals(r.getCreditEntryId(), occupation.getId()));
        if (current.compareTo(target) == 0 && (target.signum() == 0 || sameEntry)) {
            return false;
        }
        existing.forEach(r -> voidReceipt(r, "随 PI 抵扣金额调整"));
        if (target.signum() > 0 && occupation != null) {
            BigDecimal occAmount = occupation.getAmount().negate();
            BigDecimal value = occupation.getAmountCny().negate();
            BigDecimal cny = target.compareTo(occAmount) == 0 ? value
                    : value.multiply(target, MC).divide(occAmount, MC).setScale(2, RoundingMode.HALF_UP);
            insertReceipt(target, cny, occupation, pi.getTenantId(), pi.getCurrencyCode(), pi.getBusinessUnitId(), pi.getId(), soId);
        }
        return true;
    }

    private void insertReceipt(BigDecimal amount, BigDecimal cny, CustomerCreditEntryDO occupation, Integer tenantId, String currency,
                               Integer bu, Long piId, Long soId) {
        PaymentReceiptDO r = new PaymentReceiptDO();
        r.setTenantId(tenantId);
        r.setBusinessUnitId(bu);
        r.setBuMismatch(0);
        r.setPiId(piId);
        r.setSoId(soId);
        r.setKind(SalesConstants.KIND_RECEIPT);
        r.setCurrencyCode(currency);
        r.setPaymentMethod(SalesConstants.METHOD_CREDIT);
        r.setPaymentMethodName(SalesConstants.METHOD_CREDIT_NAME);
        r.setChannel(SalesConstants.CHANNEL_NONE);
        r.setPlatformOrderNo("");
        r.setAmount(amount);
        r.setPlatformFee(BigDecimal.ZERO);
        r.setNetAmount(amount);
        r.setFeeDiff(BigDecimal.ZERO);
        r.setAmountCny(cny);
        r.setNetAmountCny(cny);
        r.setExchangeRate(cny.divide(amount, 6, RoundingMode.HALF_UP));
        r.setRateSource(SalesConstants.RATE_CREDIT);
        r.setSettledAmount(BigDecimal.ZERO);
        r.setSettleStatus(SalesConstants.SETTLE_NA);
        r.setPayer("");
        r.setReceiptDate(LocalDate.now());
        r.setFileKeys("");
        r.setNote("余额抵扣，来源 " + sourceNos(occupation));
        r.setStatus(SalesConstants.RECORD_VALID);
        r.setVoidReason("");
        r.setOperatorId(currentUser.resolve());
        r.setMigrated(0);
        r.setCreditEntryId(occupation.getId());
        receiptDocs.number(r);
        receiptMapper.insert(r);
        receiptDocs.log(r, "登记", r.getNote() + "：" + r.getCurrencyCode() + " " + amount.toPlainString());
    }

    private void voidReceipt(PaymentReceiptDO r, String reason) {
        r.setStatus(SalesConstants.RECORD_VOID);
        r.setVoidReason(reason);
        receiptMapper.updateById(r);
        receiptDocs.log(r, "作废", reason);
    }

    /** 来源单据最近一条还有未释放消耗的占用 */
    private CustomerCreditEntryDO activeOccupation(Source src) {
        List<CustomerCreditEntryDO> rows = entryMapper.selectList(new LambdaQueryWrapper<CustomerCreditEntryDO>()
                .eq(CustomerCreditEntryDO::getTenantId, PiStore.tenantId())
                .eq(CustomerCreditEntryDO::getSourceType, src.type())
                .eq(CustomerCreditEntryDO::getSourceId, src.id())
                .eq(CustomerCreditEntryDO::getEntryType, FinanceConstants.CREDIT_OCCUPY)
                .isNull(CustomerCreditEntryDO::getDeletedAt)
                .orderByDesc(CustomerCreditEntryDO::getId));
        return rows.stream().filter(e -> unreleased(e).signum() > 0).findFirst().orElse(null);
    }

    private BigDecimal unreleased(CustomerCreditEntryDO out) {
        return consumptionMapper.selectList(new LambdaQueryWrapper<CustomerCreditConsumptionDO>()
                        .eq(CustomerCreditConsumptionDO::getOutEntryId, out.getId())
                        .eq(CustomerCreditConsumptionDO::getReleased, 0)
                        .isNull(CustomerCreditConsumptionDO::getDeletedAt))
                .stream().map(CustomerCreditConsumptionDO::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 占用消耗了哪些转入的来源单据编号 */
    private String sourceNos(CustomerCreditEntryDO out) {
        List<Long> ins = consumptionMapper.selectList(new LambdaQueryWrapper<CustomerCreditConsumptionDO>()
                        .eq(CustomerCreditConsumptionDO::getOutEntryId, out.getId())
                        .isNull(CustomerCreditConsumptionDO::getDeletedAt))
                .stream().map(CustomerCreditConsumptionDO::getInEntryId).distinct().toList();
        // 占用至少消耗了一笔转入
        return entryMapper.selectBatchIds(ins).stream().map(e -> e.getSourceNo().isEmpty()
                ? FinanceConstants.CREDIT_TYPE_NAMES.get(e.getEntryType()) : e.getSourceNo()).distinct()
                .collect(java.util.stream.Collectors.joining("、"));
    }

    private static BigDecimal credit(PiVersionDO v) {
        return v == null ? BigDecimal.ZERO : v.getCreditAmount();
    }

    private static String statusName(ProformaInvoiceDO pi) {
        return pi.getDeletedAt() != null ? "已删除" : SalesConstants.PI_STATUS_NAMES.getOrDefault(pi.getStatus(), "");
    }

    private static LambdaQueryWrapper<PaymentReceiptDO> valid() {
        return new LambdaQueryWrapper<PaymentReceiptDO>()
                .eq(PaymentReceiptDO::getKind, SalesConstants.KIND_RECEIPT)
                .eq(PaymentReceiptDO::getPaymentMethod, SalesConstants.METHOD_CREDIT)
                .eq(PaymentReceiptDO::getStatus, SalesConstants.RECORD_VALID)
                .isNull(PaymentReceiptDO::getDeletedAt);
    }
}
