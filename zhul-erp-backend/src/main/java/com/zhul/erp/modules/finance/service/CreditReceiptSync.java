package com.zhul.erp.modules.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.finance.constants.FinanceConstants;
import com.zhul.erp.modules.finance.entity.CustomerCreditEntryDO;
import com.zhul.erp.modules.finance.repository.CustomerCreditEntryMapper;
import com.zhul.erp.modules.finance.service.CustomerCreditLedger.Account;
import com.zhul.erp.modules.finance.service.CustomerCreditLedger.Source;
import com.zhul.erp.modules.sales.entity.ProformaInvoiceDO;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.sales.support.ReceiptPosition;
import com.zhul.erp.modules.sales.support.ReceiptPosition.Position;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 收款与客户余额的联动（spec sales/payment-receipt「代收款转入客户余额」「多收的处理」、finance/order-cost）：
 * <ul>
 *   <li>收款状态变为已到账时，代收净额（代收款 − 代收承担的平台手续费）转入客户余额，人民币 = 代收净额 × 现金加权到账汇率；
 *       不再是已到账时撤回还没被使用的代收转入，已被使用时拒绝（作废到账随之回滚）；</li>
 *   <li>已处理的多收超过当前多收时（作废了到账），从最后处理的开始撤回；已被使用时拒绝；</li>
 *   <li>订单收入要扣除的代收与多收的人民币价值。</li>
 * </ul>
 * 由 PiSummary 在每次重算收款状态后调用，与收款登记 / 作废在同一事务内。
 */
@Component
@RequiredArgsConstructor
public class CreditReceiptSync {

    private static final MathContext MC = MathContext.DECIMAL128;

    private final CustomerCreditLedger ledger;
    private final CustomerCreditEntryMapper entryMapper;
    private final ReceiptPosition receiptPosition;

    @Transactional(rollbackFor = Exception.class)
    public void afterReceipts(ProformaInvoiceDO pi) {
        sync(receiptPosition.ofPi(pi));
    }

    /** 手动订单（PI 转成的订单收款在 PI 上，不在这里处理） */
    @Transactional(rollbackFor = Exception.class)
    public void afterReceipts(SalesOrderDO o) {
        if (Objects.equals(o.getSource(), com.zhul.erp.modules.sales.constants.SalesConstants.SO_MANUAL)) {
            sync(receiptPosition.ofOrder(o));
        }
    }

    /** 订单收入要扣除的人民币：已转入的代收（锁定价值）与多收（已处理的锁定价值 + 待处理 × 现金加权到账汇率） */
    public record Deductions(BigDecimal collectionCny, BigDecimal overpaidCny, BigDecimal collectionFee,
                             BigDecimal overpaidPending) {
    }

    public Deductions deductions(Position p) {
        BigDecimal collection = active(p, FinanceConstants.CREDIT_COLLECTION_IN).stream()
                .map(CustomerCreditEntryDO::getAmountCny).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<CustomerCreditEntryDO> processed = active(p, FinanceConstants.CREDIT_OVERPAID_IN);
        BigDecimal processedAmount = processed.stream().map(CustomerCreditEntryDO::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pending = p.overpaid().subtract(processedAmount).max(BigDecimal.ZERO);
        BigDecimal rate = rate(p);
        BigDecimal overpaid = processed.stream().map(CustomerCreditEntryDO::getAmountCny).reduce(BigDecimal.ZERO, BigDecimal::add)
                .add(pending.multiply(rate, MC));
        boolean transferred = !active(p, FinanceConstants.CREDIT_COLLECTION_IN).isEmpty();
        return new Deductions(collection, overpaid.setScale(2, RoundingMode.HALF_UP),
                transferred ? p.collectionFee() : BigDecimal.ZERO, pending);
    }

    /** 还没处理的多收（原币） */
    public BigDecimal pendingOverpaid(Position p) {
        BigDecimal processed = active(p, FinanceConstants.CREDIT_OVERPAID_IN).stream()
                .map(CustomerCreditEntryDO::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return p.overpaid().subtract(processed).max(BigDecimal.ZERO);
    }

    /** 现金收款的加权到账汇率；没有现金到账时按全部收款加权 */
    public static BigDecimal rate(Position p) {
        if (p.cashRate() != null) {
            return p.cashRate();
        }
        BigDecimal gross = p.receipts().stream().map(r -> r.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cny = p.receipts().stream().map(r -> r.getAmount().multiply(r.getExchangeRate(), MC)).reduce(BigDecimal.ZERO, BigDecimal::add);
        return gross.signum() > 0 ? cny.divide(gross, 6, RoundingMode.HALF_UP) : BigDecimal.ONE;
    }

    /** 来源单据上还有效（没被冲销）的某类转入，按生成先后 */
    public List<CustomerCreditEntryDO> active(Position p, int type) {
        List<CustomerCreditEntryDO> ins = entryMapper.selectList(new LambdaQueryWrapper<CustomerCreditEntryDO>()
                .eq(CustomerCreditEntryDO::getTenantId, PiStore.tenantId())
                .eq(CustomerCreditEntryDO::getSourceType, p.ownerType())
                .eq(CustomerCreditEntryDO::getSourceId, p.ownerId())
                .eq(CustomerCreditEntryDO::getEntryType, type)
                .isNull(CustomerCreditEntryDO::getDeletedAt));
        if (ins.isEmpty()) {
            return ins;
        }
        Set<Long> reversed = entryMapper.selectList(new LambdaQueryWrapper<CustomerCreditEntryDO>()
                        .in(CustomerCreditEntryDO::getRelatedEntryId, ins.stream().map(CustomerCreditEntryDO::getId).toList())
                        .eq(CustomerCreditEntryDO::getEntryType, FinanceConstants.CREDIT_REVERSE)
                        .isNull(CustomerCreditEntryDO::getDeletedAt))
                .stream().map(CustomerCreditEntryDO::getRelatedEntryId).collect(Collectors.toSet());
        return ins.stream().filter(e -> !reversed.contains(e.getId()))
                .sorted(Comparator.comparing(CustomerCreditEntryDO::getId)).toList();
    }

    private void sync(Position p) {
        // 代收：收齐后转入一次；不再收齐时撤回
        List<CustomerCreditEntryDO> collection = active(p, FinanceConstants.CREDIT_COLLECTION_IN);
        boolean shouldHave = p.paid() && p.collectionNet().signum() > 0;
        if (shouldHave && collection.isEmpty()) {
            BigDecimal net = p.collectionNet();
            ledger.credit(account(p), FinanceConstants.CREDIT_COLLECTION_IN, net, net.multiply(rate(p), MC),
                    new Source(p.ownerType(), p.ownerId(), p.ownerNo()),
                    "代收 " + p.currencyCode() + " " + CustomerCreditLedger.fmt(p.collection()) + " − 客户承担的平台手续费 "
                            + CustomerCreditLedger.fmt(p.collectionFee()));
        } else if (!shouldHave) {
            for (CustomerCreditEntryDO e : collection) {
                reverseOrExplain(e, "收款不再收齐，撤回代收", "代收已转给客户，不能作废这笔到账");
            }
        }
        // 多收：已处理的超过当前多收时，从最后处理的开始撤回
        List<CustomerCreditEntryDO> processed = active(p, FinanceConstants.CREDIT_OVERPAID_IN);
        BigDecimal sum = processed.stream().map(CustomerCreditEntryDO::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        for (int i = processed.size() - 1; i >= 0 && sum.compareTo(p.overpaid()) > 0; i--) {
            CustomerCreditEntryDO e = processed.get(i);
            reverseOrExplain(e, "多收减少，撤回已处理的多收", "多收已转给客户，不能作废这笔到账");
            sum = sum.subtract(e.getAmount());
        }
    }

    private void reverseOrExplain(CustomerCreditEntryDO e, String remark, String usedMessage) {
        if (!ledger.unused(e)) {
            throw new com.zhul.erp.common.exception.BizException(usedMessage + "（已被 " + ledger.usedBy(e) + " 使用）");
        }
        ledger.reverse(e, remark);
    }

    private static Account account(Position p) {
        return new Account(p.customerId(), p.currencyCode(), p.businessUnitId());
    }
}
