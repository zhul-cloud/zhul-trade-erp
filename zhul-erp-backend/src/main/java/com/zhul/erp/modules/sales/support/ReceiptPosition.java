package com.zhul.erp.modules.sales.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.sales.constants.SalesConstants;
import com.zhul.erp.modules.sales.entity.PaymentReceiptDO;
import com.zhul.erp.modules.sales.entity.PiFeeDO;
import com.zhul.erp.modules.sales.entity.PiVersionDO;
import com.zhul.erp.modules.sales.entity.ProformaInvoiceDO;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.entity.SalesOrderFeeDO;
import com.zhul.erp.modules.sales.repository.PaymentReceiptMapper;
import com.zhul.erp.modules.sales.repository.ProformaInvoiceMapper;
import com.zhul.erp.modules.sales.repository.SalesOrderFeeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * PI 或手动订单的收款位置（spec sales/payment-receipt、finance/customer-credit）：合计、代收款、已到账（含余额抵扣）、
 * 多收、平台手续费与现金收款的加权到账汇率。收款记录属于 PI；PI 转成的订单取 PI 的位置。
 * <ul>
 *   <li>多收 = 已到账 + 手续费差额 − 合计（不小于 0）；</li>
 *   <li>代收承担的平台手续费 = 平台手续费合计 × 代收款 ÷ 合计，由客户承担；</li>
 *   <li>现金加权到账汇率 = Σ 现金到账毛额 × 到账汇率 ÷ Σ 现金到账毛额（不含余额抵扣），6 位 HALF_UP。</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class ReceiptPosition {

    private static final MathContext MC = MathContext.DECIMAL128;

    private final PaymentReceiptMapper receiptMapper;
    private final SalesOrderFeeMapper orderFeeMapper;
    private final ProformaInvoiceMapper piMapper;
    private final PiStore store;

    public record Position(String ownerType, Long ownerId, String ownerNo, Long customerId, String currencyCode,
                           Integer businessUnitId, BigDecimal total, BigDecimal collection, BigDecimal received,
                           BigDecimal feeDiff, BigDecimal creditReceived, BigDecimal platformFee, BigDecimal cashGross,
                           BigDecimal cashGrossCny, boolean hasOnline, List<PaymentReceiptDO> receipts) {

        /** 已到账 + 手续费差额超过合计的部分 */
        public BigDecimal overpaid() {
            return received.add(feeDiff).subtract(total).max(BigDecimal.ZERO);
        }

        /** 真实货款：合计 − 代收款 */
        public BigDecimal goods() {
            return total.subtract(collection);
        }

        /** 收款状态是否已到账 */
        public boolean paid() {
            return received.signum() > 0 && received.add(feeDiff).compareTo(total) >= 0;
        }

        /** 现金收款的加权到账汇率；没有现金到账时为空 */
        public BigDecimal cashRate() {
            return cashGross.signum() > 0 ? cashGrossCny.divide(cashGross, 6, RoundingMode.HALF_UP) : null;
        }

        /** 代收承担的平台手续费（原币，HALF_UP 两位） */
        public BigDecimal collectionFee() {
            if (collection.signum() <= 0 || total.signum() <= 0 || platformFee.signum() <= 0) {
                return BigDecimal.ZERO.setScale(2);
            }
            return platformFee.multiply(collection, MC).divide(total, MC).setScale(2, RoundingMode.HALF_UP);
        }

        /** 代收净额 = 代收款 − 代收承担的平台手续费 */
        public BigDecimal collectionNet() {
            return collection.subtract(collectionFee());
        }
    }

    public Position ofPi(ProformaInvoiceDO pi) {
        PiVersionDO v = store.version(pi.getId(), pi.getCurrentVersionNo() != null && pi.getCurrentVersionNo() > 0
                ? pi.getCurrentVersionNo() : pi.getEditingVersionNo());
        BigDecimal collection = v == null ? BigDecimal.ZERO : store.fees(v.getId()).stream()
                .filter(f -> Objects.equals(f.getFeeType(), SalesConstants.FEE_COLLECTION))
                .map(PiFeeDO::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<PaymentReceiptDO> receipts = receiptMapper.selectList(valid().eq(PaymentReceiptDO::getPiId, pi.getId()));
        return build("PI", pi.getId(), pi.getPiNo(), pi.getCustomerId(), pi.getCurrencyCode(), pi.getBusinessUnitId(),
                nz(pi.getTotalAmount()), collection, receipts);
    }

    /** 订单：PI 转成的取 PI 的收款，手动订单取订单自己的收款 */
    public Position ofOrder(SalesOrderDO o) {
        // 手动订单的 pi_id 为空或 0，按来源判断
        if (!Objects.equals(o.getSource(), SalesConstants.SO_MANUAL)) {
            // 财务按订单处理，不受 PI 创建人的数据范围限制
            ProformaInvoiceDO pi = piMapper.selectById(o.getPiId());
            Position p = ofPi(pi);
            return new Position(p.ownerType(), p.ownerId(), p.ownerNo(), o.getCustomerId(), o.getCurrencyCode(), o.getBusinessUnitId(),
                    p.total(), p.collection(), p.received(), p.feeDiff(), p.creditReceived(), p.platformFee(), p.cashGross(),
                    p.cashGrossCny(), p.hasOnline(), p.receipts());
        }
        BigDecimal collection = orderFeeMapper.selectList(new LambdaQueryWrapper<SalesOrderFeeDO>()
                        .eq(SalesOrderFeeDO::getSoId, o.getId())
                        .eq(SalesOrderFeeDO::getFeeType, SalesConstants.FEE_COLLECTION)
                        .isNull(SalesOrderFeeDO::getDeletedAt))
                .stream().map(SalesOrderFeeDO::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<PaymentReceiptDO> receipts = receiptMapper.selectList(valid().eq(PaymentReceiptDO::getSoId, o.getId()));
        return build("SO", o.getId(), o.getSoNo(), o.getCustomerId(), o.getCurrencyCode(), o.getBusinessUnitId(),
                nz(o.getTotalAmount()), collection, receipts);
    }

    public static boolean isCredit(PaymentReceiptDO r) {
        return SalesConstants.METHOD_CREDIT.equals(r.getPaymentMethod());
    }

    private Position build(String type, Long id, String no, Long customerId, String currency, Integer bu, BigDecimal total,
                           BigDecimal collection, List<PaymentReceiptDO> receipts) {
        BigDecimal received = BigDecimal.ZERO;
        BigDecimal feeDiff = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        BigDecimal fee = BigDecimal.ZERO;
        BigDecimal cash = BigDecimal.ZERO;
        BigDecimal cashCny = BigDecimal.ZERO;
        boolean online = false;
        for (PaymentReceiptDO r : receipts) {
            received = received.add(nz(r.getAmount()));
            feeDiff = feeDiff.add(nz(r.getFeeDiff()));
            fee = fee.add(nz(r.getPlatformFee()));
            if (isCredit(r)) {
                credit = credit.add(nz(r.getAmount()));
            } else {
                cash = cash.add(nz(r.getAmount()));
                cashCny = cashCny.add(nz(r.getAmount()).multiply(nz(r.getExchangeRate()), MC));
                online |= Objects.equals(r.getChannel(), SalesConstants.CHANNEL_ONLINE);
            }
        }
        return new Position(type, id, no, customerId, currency, bu, total, collection, received, feeDiff, credit, fee, cash, cashCny,
                online, receipts);
    }

    private static LambdaQueryWrapper<PaymentReceiptDO> valid() {
        return new LambdaQueryWrapper<PaymentReceiptDO>()
                .eq(PaymentReceiptDO::getKind, SalesConstants.KIND_RECEIPT)
                .eq(PaymentReceiptDO::getStatus, SalesConstants.RECORD_VALID)
                .isNull(PaymentReceiptDO::getDeletedAt);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
