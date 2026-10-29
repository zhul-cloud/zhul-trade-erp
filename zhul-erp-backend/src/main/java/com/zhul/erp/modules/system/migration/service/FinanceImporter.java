package com.zhul.erp.modules.system.migration.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.modules.finance.constants.FinanceConstants;
import com.zhul.erp.modules.finance.entity.CustomerRefundDO;
import com.zhul.erp.modules.finance.entity.ExpenseDO;
import com.zhul.erp.modules.finance.entity.FxSettlementAllocationDO;
import com.zhul.erp.modules.finance.entity.FxSettlementDO;
import com.zhul.erp.modules.finance.entity.OtherIncomeDO;
import com.zhul.erp.modules.finance.entity.SalesOrderCostSnapshotDO;
import com.zhul.erp.modules.finance.repository.CustomerRefundMapper;
import com.zhul.erp.modules.finance.repository.ExpenseMapper;
import com.zhul.erp.modules.finance.repository.FxSettlementAllocationMapper;
import com.zhul.erp.modules.finance.repository.FxSettlementMapper;
import com.zhul.erp.modules.finance.repository.OtherIncomeMapper;
import com.zhul.erp.modules.finance.repository.SalesOrderCostSnapshotMapper;
import com.zhul.erp.modules.sales.constants.SalesConstants;
import com.zhul.erp.modules.sales.entity.PaymentReceiptDO;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.repository.PaymentReceiptMapper;
import com.zhul.erp.modules.sales.repository.SalesOrderMapper;
import com.zhul.erp.modules.system.constants.DocumentType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Map;

import static com.zhul.erp.modules.system.migration.service.MigrationRun.cut;
import static com.zhul.erp.modules.system.migration.service.MigrationRun.date;
import static com.zhul.erp.modules.system.migration.service.MigrationRun.money;
import static com.zhul.erp.modules.system.migration.service.MigrationRun.text;
import static com.zhul.erp.modules.system.migration.service.MigrationRun.textOrNull;
import static com.zhul.erp.modules.system.migration.service.MigrationRun.time;

/** 财务阶段：到账与结汇（结汇按实收、迁移生成）、退款单、按月费用单与其他收入、订单成本核算快照 */
@Component
@RequiredArgsConstructor
public class FinanceImporter {

    static final String STAGE = "财务";
    private static final Map<String, String> METHOD_NAMES = Map.of("TT", "银行转账", "WECHAT", "微信", "ALIPAY", "支付宝",
            "PAYPAL", "PayPal", "ALIBABA_TA", "阿里巴巴信用保障", "MIC", "中国制造网");

    private final SalesOrderMapper orderMapper;
    private final PaymentReceiptMapper receiptMapper;
    private final FxSettlementMapper settlementMapper;
    private final FxSettlementAllocationMapper allocationMapper;
    private final CustomerRefundMapper refundMapper;
    private final ExpenseMapper expenseMapper;
    private final OtherIncomeMapper incomeMapper;
    private final SalesOrderCostSnapshotMapper snapshotMapper;

    void run(MigrationRun r, Integer businessUnit) {
        for (JsonNode o : r.list("orders")) {
            Long soId = r.mapped("orders", text(o, "key"), "sales_order");
            if (soId == null) {
                continue;
            }
            for (JsonNode rc : o.path("receipts")) {
                receipt(r, o, soId, rc, businessUnit);
            }
            snapshot(r, text(o, "key"), soId, o.path("snapshot"));
        }
        JsonNode finance = r.bundle.get("finance");
        if (finance == null) {
            return;
        }
        for (JsonNode f : finance.path("refunds")) {
            refund(r, f);
        }
        for (JsonNode e : finance.path("expenses")) {
            expense(r, e);
        }
        for (JsonNode i : finance.path("otherIncomes")) {
            income(r, i);
        }
    }

    private void receipt(MigrationRun r, JsonNode o, Long soId, JsonNode rc, Integer bu) {
        String key = text(rc, "key");
        if (r.mapped("fxSettlements", key, "payment_receipt") != null) {
            r.skipped(STAGE, "到账");
            return;
        }
        String method = text(o, "paymentMethod");
        boolean settled = rc.path("settled").asBoolean();
        BigDecimal rate = new BigDecimal(text(rc, "exchangeRate"));
        PaymentReceiptDO d = new PaymentReceiptDO();
        d.setTenantId(r.tenantId);
        d.setBusinessUnitId(bu);
        d.setBuMismatch(0);
        d.setSoId(soId);
        d.setKind(SalesConstants.KIND_RECEIPT);
        d.setCurrencyCode(text(rc, "currency"));
        d.setPaymentMethod(method);
        d.setPaymentMethodName(METHOD_NAMES.getOrDefault(method, method));
        d.setChannel("ALIBABA_TA".equals(method) || "MIC".equals(method) ? SalesConstants.CHANNEL_ONLINE : SalesConstants.CHANNEL_OFFLINE);
        d.setPlatformOrderNo(cut(text(rc, "platformOrderNo"), 64));
        d.setAmount(money(rc, "amount"));
        d.setExchangeRate(rate);
        d.setAmountCny(money(rc, "amountCny"));
        d.setFeeDiff(BigDecimal.ZERO);
        d.setPlatformFee(money(rc, "platformFee"));
        d.setNetAmount(money(rc, "netAmount"));
        d.setNetAmountCny(money(rc, "netAmountCny"));
        d.setRateSource(settled ? 2 : 1);
        // 切换日之前的到账一律视为已结汇（旧系统没记的结汇已在线下完成），不参与之后结汇的先进先出
        d.setSettledAmount(d.getNetAmount());
        d.setSettleStatus(SalesConstants.SETTLE_DONE);
        d.setReceiptDate(date(rc, "receiptDate"));
        d.setFileKeys("[]");
        d.setNote(cut(text(rc, "note"), 300));
        d.setStatus(SalesConstants.RECORD_VALID);
        Long operator = r.user(text(rc, "operator"));
        d.setOperatorId(operator == null ? 0L : operator);
        d.setMigrated(1);
        d.setCreateTime(time(rc, "createTime"));
        d.setCreateBy(r.username(text(rc, "operator")));
        receiptMapper.insert(d);
        r.record("fxSettlements", key, "payment_receipt", d.getId());
        r.created(STAGE, "到账");

        JsonNode s = rc.path("settlement");
        if (s.isObject()) {
            LocalDate day = date(s, "date");
            FxSettlementDO x = new FxSettlementDO();
            x.setTenantId(r.tenantId);
            x.setBusinessUnitId(bu);
            x.setSettleNo(r.nextNo(DocumentType.JH, day));
            x.setCurrencyCode(d.getCurrencyCode());
            x.setSettleDate(day);
            x.setAmount(money(s, "amount"));
            x.setAmountCny(money(s, "amountCny"));
            x.setExchangeRate(new BigDecimal(text(s, "exchangeRate")).setScale(6, RoundingMode.HALF_UP));
            x.setRemark("历史迁移");
            x.setMigrated(1);
            x.setStatus(FinanceConstants.FX_VALID);
            x.setCreateTime(d.getCreateTime());
            x.setCreateBy(d.getCreateBy());
            settlementMapper.insert(x);
            FxSettlementAllocationDO a = new FxSettlementAllocationDO();
            a.setTenantId(r.tenantId);
            a.setSettlementId(x.getId());
            a.setReceiptId(d.getId());
            a.setAmount(x.getAmount());
            a.setAmountCny(x.getAmountCny());
            a.setSortOrder(1);
            allocationMapper.insert(a);
            r.record("fxSettlements", key, "fin_fx_settlement", x.getId());
            r.created(STAGE, "结汇");
        }
    }

    private void snapshot(MigrationRun r, String soKey, Long soId, JsonNode s) {
        if (!s.isObject() || r.mapped("costSheets", soKey, "sales_order_cost_snapshot") != null) {
            return;
        }
        SalesOrderCostSnapshotDO d = new SalesOrderCostSnapshotDO();
        d.setTenantId(r.tenantId);
        d.setSoId(soId);
        d.setCurrencyCode("CNY");
        d.setRevenue(money(s, "revenue"));
        d.setRefund(money(s, "refund"));
        d.setPurchaseAmount(money(s, "purchase"));
        d.setFreight(money(s, "freight"));
        d.setSurcharge(money(s, "surcharge"));
        d.setServiceFee(money(s, "serviceFee"));
        d.setCost(money(s, "cost"));
        d.setGrossProfit(money(s, "grossProfit"));
        d.setMarginRate(textOrNull(s, "marginRate") == null ? null : new BigDecimal(text(s, "marginRate")));
        // 切换日之前的单据一律完结：核算单一律记为已结算
        d.setSettled(1);
        d.setAccountingMonth(cut(text(s, "accountingMonth"), 7));
        StringBuilder codes = new StringBuilder();
        s.path("sourceCodes").forEach(c -> codes.append(codes.isEmpty() ? "" : ",").append(c.asText()));
        d.setSourceCodes(cut(codes.toString(), 200));
        d.setCreateBy("migration");
        snapshotMapper.insert(d);
        r.record("costSheets", soKey, "sales_order_cost_snapshot", d.getId());
        r.created(STAGE, "成本核算快照");
    }

    private void refund(MigrationRun r, JsonNode f) {
        String key = text(f, "key");
        if (r.mapped("refunds", key, "fin_customer_refund") != null) {
            r.skipped(STAGE, "退款单");
            return;
        }
        Long soId = r.mapped("orders", text(f, "soKey"), "sales_order");
        if (soId == null) {
            r.failed(STAGE, "退款单（订单没迁移）");
            return;
        }
        SalesOrderDO so = orderMapper.selectById(soId);
        LocalDate day = date(f, "refundDate");
        BigDecimal cny = money(f, "amountCny");
        BigDecimal rate = so.getExchangeRate();
        CustomerRefundDO d = new CustomerRefundDO();
        d.setTenantId(r.tenantId);
        d.setRefundNo(r.nextNo(DocumentType.TK, day));
        d.setSoId(soId);
        d.setRefundDate(day);
        d.setReason("OTHER");
        d.setCurrencyCode(so.getCurrencyCode());
        d.setAmount(cny.divide(rate, 2, RoundingMode.HALF_UP));
        d.setExchangeRate(cny.divide(d.getAmount().signum() == 0 ? BigDecimal.ONE : d.getAmount(), 6, RoundingMode.HALF_UP));
        d.setAmountCny(cny);
        d.setMethod(2);
        d.setRemark("历史迁移");
        d.setStatus(FinanceConstants.RECORD_VALID);
        d.setMigrated(1);
        d.setCreateBy("migration");
        refundMapper.insert(d);
        r.record("refunds", key, "fin_customer_refund", d.getId());
        r.created(STAGE, "退款单");
    }

    private void expense(MigrationRun r, JsonNode e) {
        String key = text(e, "key");
        if (r.mapped("expenses", key, "fin_expense") != null) {
            r.skipped(STAGE, "费用单");
            return;
        }
        LocalDate day = date(e, "date");
        ExpenseDO d = new ExpenseDO();
        d.setTenantId(r.tenantId);
        d.setExpenseNo(r.nextNo(DocumentType.FE, day));
        d.setName(cut(text(e, "name"), 64));
        d.setCategory(text(e, "category"));
        d.setAmortizeMonths(1);
        d.setOccurDate(day);
        d.setPayeeType(4);
        d.setCurrencyCode("CNY");
        d.setAmount(money(e, "amount"));
        d.setExchangeRate(BigDecimal.ONE);
        d.setAmountCny(d.getAmount());
        d.setMethod(FinanceConstants.METHOD_COMPANY);
        d.setRemark("历史迁移：旧系统公司经营报表");
        d.setStatus(FinanceConstants.FE_VALID);
        d.setMigrated(1);
        d.setCreateBy("migration");
        expenseMapper.insert(d);
        r.record("expenses", key, "fin_expense", d.getId());
        r.created(STAGE, "费用单");
    }

    private void income(MigrationRun r, JsonNode i) {
        String key = text(i, "key");
        if (r.mapped("otherIncomes", key, "fin_other_income") != null) {
            r.skipped(STAGE, "其他收入");
            return;
        }
        LocalDate day = date(i, "date");
        OtherIncomeDO d = new OtherIncomeDO();
        d.setTenantId(r.tenantId);
        d.setIncomeNo(r.nextNo(DocumentType.OI, day));
        d.setIncomeDate(day);
        d.setCategory(text(i, "category"));
        d.setAmount(money(i, "amount"));
        d.setRemark("历史迁移：旧系统公司经营报表");
        d.setStatus(FinanceConstants.RECORD_VALID);
        d.setMigrated(1);
        d.setCreateBy("migration");
        incomeMapper.insert(d);
        r.record("otherIncomes", key, "fin_other_income", d.getId());
        r.created(STAGE, "其他收入");
    }
}
