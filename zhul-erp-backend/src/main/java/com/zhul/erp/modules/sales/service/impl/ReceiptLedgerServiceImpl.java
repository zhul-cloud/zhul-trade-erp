package com.zhul.erp.modules.sales.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.masterdata.repository.CustomerMapper;
import com.zhul.erp.modules.sales.constants.SalesConstants;
import com.zhul.erp.modules.sales.dto.ReceiptRecordPageVO;
import com.zhul.erp.modules.sales.dto.ReceiptRecordQuery;
import com.zhul.erp.modules.sales.dto.ReceiptRowVO;
import com.zhul.erp.modules.sales.dto.UnclaimedListVO;
import com.zhul.erp.modules.sales.dto.UnclaimedReceiptRequest;
import com.zhul.erp.modules.sales.entity.PaymentReceiptDO;
import com.zhul.erp.modules.sales.entity.ProformaInvoiceDO;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.repository.PaymentReceiptMapper;
import com.zhul.erp.modules.sales.repository.ProformaInvoiceMapper;
import com.zhul.erp.modules.sales.service.ReceiptLedgerService;
import com.zhul.erp.modules.sales.support.PaymentMethods;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.sales.support.PiSummary;
import com.zhul.erp.modules.sales.support.ReceiptMoney;
import com.zhul.erp.modules.sales.support.ReceiptRows;
import com.zhul.erp.modules.system.entity.BankAccountDO;
import com.zhul.erp.modules.system.service.BankAccountService;
import com.zhul.erp.modules.system.service.ExchangeRateService;
import com.zhul.erp.modules.system.service.LogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class ReceiptLedgerServiceImpl implements ReceiptLedgerService {

    private static final int RECENT_CLAIMED = 20;
    private static final int KEYWORD_LIMIT = 500;

    private final PaymentReceiptMapper receiptMapper;
    private final ProformaInvoiceMapper piMapper;
    private final CustomerMapper customerMapper;
    private final PiStore store;
    private final PiSummary summary;
    private final PaymentMethods paymentMethods;
    private final ReceiptRows receiptRows;
    private final BankAccountService bankAccountService;
    private final ExchangeRateService exchangeRateService;
    private final CurrentUserResolver currentUser;
    private final com.zhul.erp.modules.sales.repository.SalesOrderMapper orderMapper;
    private final LogService logService;

    // ---------------------------------------------------------------- 未认领到账

    @Override
    public UnclaimedListVO unclaimed(String keyword, String currencyCode) {
        int tenant = PiStore.tenantId();
        LambdaQueryWrapper<PaymentReceiptDO> w = base(tenant)
                .isNull(PaymentReceiptDO::getPiId)
                .isNull(PaymentReceiptDO::getSoId)
                .eq(PaymentReceiptDO::getStatus, SalesConstants.RECORD_VALID);
        if (StringUtils.hasText(currencyCode)) {
            w.eq(PaymentReceiptDO::getCurrencyCode, currencyCode.trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            w.and(x -> x.like(PaymentReceiptDO::getPayer, kw).or().like(PaymentReceiptDO::getNote, kw)
                    .or().apply("CAST(amount AS CHAR) LIKE {0}", "%" + kw.replace(",", "") + "%"));
        }
        w.orderByDesc(PaymentReceiptDO::getReceiptDate).orderByDesc(PaymentReceiptDO::getId);
        UnclaimedListVO vo = new UnclaimedListVO();
        vo.setUnclaimed(receiptRows.of(receiptMapper.selectList(w)));
        vo.setRecentClaimed(receiptRows.of(receiptMapper.selectList(base(tenant)
                .isNotNull(PaymentReceiptDO::getClaimedBy)
                .and(x -> x.isNotNull(PaymentReceiptDO::getPiId).or().isNotNull(PaymentReceiptDO::getSoId))
                .eq(PaymentReceiptDO::getStatus, SalesConstants.RECORD_VALID)
                .orderByDesc(PaymentReceiptDO::getClaimedAt)
                .last("LIMIT " + RECENT_CLAIMED))));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReceiptRowVO registerUnclaimed(UnclaimedReceiptRequest req) {
        String currency = req.getCurrencyCode().trim().toUpperCase(Locale.ROOT);
        if (req.getAmount().signum() <= 0) {
            throw new BizException("到账金额需要大于 0");
        }
        BankAccountDO bank = bankAccountService.requireEnabled(req.getBankAccountId());
        if (!bank.getCurrencyCode().equals(currency)) {
            throw new BizException("收款账户币种（" + bank.getCurrencyCode() + "）与到账币种（" + currency + "）不同");
        }
        PaymentMethods.Method method = paymentMethods.offline(req.getPaymentMethod());
        BigDecimal rate = "CNY".equals(currency) ? BigDecimal.ONE.setScale(6) : exchangeRateService.require(currency).rate();
        PaymentReceiptDO r = new PaymentReceiptDO();
        r.setTenantId(PiStore.tenantId());
        r.setPiId(null);
        r.setKind(SalesConstants.KIND_RECEIPT);
        r.setCurrencyCode(currency);
        PaymentReceiptServiceImpl.setMethod(r, method);
        ReceiptMoney.apply(r, req.getAmount().setScale(2, RoundingMode.HALF_UP), BigDecimal.ZERO, rate, req.getActualAmountCny());
        r.setFeeDiff(BigDecimal.ZERO);
        r.setPayer(trim(req.getPayer(), 128));
        r.setReceiptDate(req.getReceiptDate());
        r.setBankAccountId(bank.getId());
        r.setFileKeys("[]");
        r.setNote(trim(req.getNote(), 300));
        r.setStatus(SalesConstants.RECORD_VALID);
        r.setVoidReason("");
        r.setOperatorId(currentUserId());
        receiptMapper.insert(r);
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("amount", currency + " " + r.getAmount());
        after.put("payer", r.getPayer());
        after.put("netAmountCny", r.getNetAmountCny());
        logService.recordOperateLog(SalesConstants.MENU_RECEIPTS, "登记未认领到账", null, after);
        return receiptRows.of(List.of(r)).get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unclaim(Long id, String reason) {
        String why = requireReason(reason);
        PaymentReceiptDO found = require(id);
        if ((found.getPiId() == null && found.getSoId() == null) || found.getClaimedBy() == null) {
            throw new BizException("这笔到账不是认领来的，不能取消认领");
        }
        // 先锁 PI / 订单再锁收款记录（与认领的加锁顺序一致）
        ProformaInvoiceDO pi = found.getPiId() == null ? null : store.lockVisible(found.getPiId());
        SalesOrderDO so = found.getSoId() == null ? null : lockOrder(found.getSoId());
        PaymentReceiptDO r = lockRow(id);
        if (!Objects.equals(r.getPiId(), pi == null ? null : pi.getId()) || !Objects.equals(r.getSoId(), so == null ? null : so.getId())
                || r.getStatus() != SalesConstants.RECORD_VALID) {
            throw new BizException("这笔到账状态已变化，请刷新后再试");
        }
        r.setPiId(null);
        r.setSoId(null);
        r.setSlipId(null);
        r.setClaimedBy(null);
        r.setClaimedAt(null);
        receiptMapper.updateById(r);
        if (pi != null) {
            summary.refresh(pi);
        } else {
            summary.refreshOrder(so);
        }
        logService.recordOperateLog(SalesConstants.MENU_RECEIPTS, "取消认领",
                Map.of(pi != null ? "piNo" : "soNo", pi != null ? pi.getPiNo() : so.getSoNo(), "amount", r.getCurrencyCode() + " " + r.getAmount()),
                Map.of("reason", why));
    }

    private SalesOrderDO lockOrder(Long soId) {
        SalesOrderDO so = orderMapper.selectOne(new LambdaQueryWrapper<SalesOrderDO>().eq(SalesOrderDO::getId, soId).last("FOR UPDATE"));
        if (so == null || so.getDeletedAt() != null || !Objects.equals(so.getTenantId(), PiStore.tenantId())) {
            throw new BizException("销售订单不存在");
        }
        return so;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void voidUnclaimed(Long id, String reason) {
        String why = requireReason(reason);
        require(id);
        PaymentReceiptDO r = lockRow(id);
        if (r.getPiId() != null || r.getSoId() != null) {
            throw new BizException("这笔到账已被认领，请在 PI 或订单上作废，或先取消认领");
        }
        if (r.getStatus() == SalesConstants.RECORD_VOID) {
            throw new BizException("这笔到账已作废");
        }
        r.setStatus(SalesConstants.RECORD_VOID);
        r.setVoidReason(trim(why, 200));
        receiptMapper.updateById(r);
        logService.recordOperateLog(SalesConstants.MENU_RECEIPTS, "作废未认领到账",
                Map.of("amount", r.getCurrencyCode() + " " + r.getAmount(), "payer", r.getPayer()), Map.of("reason", r.getVoidReason()));
    }

    // ---------------------------------------------------------------- 收款记录

    @Override
    public ReceiptRecordPageVO records(ReceiptRecordQuery q) {
        int tenant = PiStore.tenantId();
        LambdaQueryWrapper<PaymentReceiptDO> w = base(tenant)
                .and(x -> x.isNotNull(PaymentReceiptDO::getPiId).or().isNotNull(PaymentReceiptDO::getSoId));
        if (!Boolean.TRUE.equals(q.getIncludeVoid())) {
            w.eq(PaymentReceiptDO::getStatus, SalesConstants.RECORD_VALID);
        }
        if (q.getDateFrom() != null) {
            w.ge(PaymentReceiptDO::getReceiptDate, q.getDateFrom());
        }
        if (q.getDateTo() != null) {
            w.le(PaymentReceiptDO::getReceiptDate, q.getDateTo());
        }
        if (StringUtils.hasText(q.getPaymentMethod())) {
            w.eq(PaymentReceiptDO::getPaymentMethod, q.getPaymentMethod().trim());
        }
        if (q.getChannel() != null) {
            w.eq(PaymentReceiptDO::getChannel, q.getChannel());
        }
        if (StringUtils.hasText(q.getCurrencyCode())) {
            w.eq(PaymentReceiptDO::getCurrencyCode, q.getCurrencyCode().trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(q.getKeyword())) {
            String kw = q.getKeyword().trim();
            List<Long> customerIds = customerMapper.selectList(new LambdaQueryWrapper<CustomerDO>()
                            .select(CustomerDO::getId)
                            .eq(CustomerDO::getTenantId, tenant)
                            .and(x -> x.like(CustomerDO::getName, kw).or().like(CustomerDO::getShortName, kw)
                                    .or().like(CustomerDO::getContactName, kw))
                            .last("LIMIT " + KEYWORD_LIMIT))
                    .stream().map(CustomerDO::getId).toList();
            LambdaQueryWrapper<ProformaInvoiceDO> pw = new LambdaQueryWrapper<ProformaInvoiceDO>()
                    .select(ProformaInvoiceDO::getId)
                    .eq(ProformaInvoiceDO::getTenantId, tenant)
                    .and(x -> {
                        x.like(ProformaInvoiceDO::getPiNo, kw);
                        if (!customerIds.isEmpty()) {
                            x.or().in(ProformaInvoiceDO::getCustomerId, customerIds);
                        }
                    })
                    .last("LIMIT " + KEYWORD_LIMIT);
            List<Long> piIds = piMapper.selectList(pw).stream().map(ProformaInvoiceDO::getId).toList();
            List<Long> soIds = orderMapper.selectList(new LambdaQueryWrapper<SalesOrderDO>()
                            .select(SalesOrderDO::getId)
                            .eq(SalesOrderDO::getTenantId, tenant)
                            .eq(SalesOrderDO::getSource, SalesConstants.SO_MANUAL)
                            .and(x -> {
                                x.like(SalesOrderDO::getSoNo, kw);
                                if (!customerIds.isEmpty()) {
                                    x.or().in(SalesOrderDO::getCustomerId, customerIds);
                                }
                            })
                            .last("LIMIT " + KEYWORD_LIMIT))
                    .stream().map(SalesOrderDO::getId).toList();
            w.and(x -> {
                x.like(PaymentReceiptDO::getPlatformOrderNo, kw).or().like(PaymentReceiptDO::getPayer, kw);
                if (!piIds.isEmpty()) {
                    x.or().in(PaymentReceiptDO::getPiId, piIds);
                }
                if (!soIds.isEmpty()) {
                    x.or().in(PaymentReceiptDO::getSoId, soIds);
                }
            });
        }
        // 汇总按筛选结果（只算有效到账），列表分页
        List<PaymentReceiptDO> all = receiptMapper.selectList(w.clone().eq(PaymentReceiptDO::getStatus, SalesConstants.RECORD_VALID));
        ReceiptRecordPageVO vo = new ReceiptRecordPageVO();
        vo.setSummary(List.of(summarize(all, SalesConstants.CHANNEL_ONLINE), summarize(all, SalesConstants.CHANNEL_OFFLINE), summarize(all, 0)));
        long total = receiptMapper.selectCount(w);
        int size = q.getPageSize() == null || q.getPageSize() <= 0 ? 20 : Math.min(q.getPageSize(), 100);
        int page = Math.max(1, q.getPage() == null ? 1 : q.getPage());
        w.orderByDesc(PaymentReceiptDO::getReceiptDate).orderByDesc(PaymentReceiptDO::getId)
                .last("LIMIT " + (long) (page - 1) * size + ", " + size);
        vo.setTotal(total);
        vo.setRecords(total == 0 ? List.of() : receiptRows.of(receiptMapper.selectList(w)));
        return vo;
    }

    /** channel 为 0 时汇总全部 */
    private static ReceiptRecordPageVO.Summary summarize(List<PaymentReceiptDO> rows, int channel) {
        Map<String, BigDecimal> amounts = new TreeMap<>();
        Map<String, BigDecimal> fees = new TreeMap<>();
        BigDecimal netCny = BigDecimal.ZERO;
        long count = 0;
        for (PaymentReceiptDO r : rows) {
            if (channel != 0 && !Objects.equals(r.getChannel(), channel)) {
                continue;
            }
            count++;
            amounts.merge(r.getCurrencyCode(), r.getAmount(), BigDecimal::add);
            BigDecimal fee = nz(r.getPlatformFee()).add(nz(r.getFeeDiff()));
            if (fee.signum() > 0) {
                fees.merge(r.getCurrencyCode(), fee, BigDecimal::add);
            }
            netCny = netCny.add(nz(r.getNetAmountCny()));
        }
        ReceiptRecordPageVO.Summary s = new ReceiptRecordPageVO.Summary();
        s.setChannel(channel);
        s.setCount(count);
        s.setAmounts(money(amounts));
        s.setFees(money(fees));
        s.setNetAmountCny(netCny);
        return s;
    }

    private static List<ReceiptRecordPageVO.Money> money(Map<String, BigDecimal> map) {
        List<ReceiptRecordPageVO.Money> list = new ArrayList<>(map.size());
        map.forEach((c, a) -> {
            ReceiptRecordPageVO.Money m = new ReceiptRecordPageVO.Money();
            m.setCurrencyCode(c);
            m.setAmount(a);
            list.add(m);
        });
        return list;
    }

    // ---------------------------------------------------------------- 内部

    private LambdaQueryWrapper<PaymentReceiptDO> base(int tenant) {
        return new LambdaQueryWrapper<PaymentReceiptDO>()
                .eq(PaymentReceiptDO::getTenantId, tenant)
                .eq(PaymentReceiptDO::getKind, SalesConstants.KIND_RECEIPT)
                .isNull(PaymentReceiptDO::getDeletedAt);
    }

    private PaymentReceiptDO require(Long id) {
        PaymentReceiptDO r = id == null ? null : receiptMapper.selectById(id);
        if (r == null || r.getDeletedAt() != null || !Objects.equals(r.getTenantId(), PiStore.tenantId())
                || r.getKind() != SalesConstants.KIND_RECEIPT) {
            throw new BizException("到账记录不存在");
        }
        return r;
    }

    private PaymentReceiptDO lockRow(Long id) {
        return receiptMapper.selectOne(new LambdaQueryWrapper<PaymentReceiptDO>().eq(PaymentReceiptDO::getId, id).last("FOR UPDATE"));
    }

    private static String requireReason(String reason) {
        String why = reason == null ? "" : reason.trim();
        if (why.isEmpty()) {
            throw new BizException("请填写原因");
        }
        return why;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private Long currentUserId() {
        Long id = currentUser.resolve();
        return id == null ? 0L : id;
    }

    private static String trim(String s, int max) {
        String t = s == null ? "" : s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
