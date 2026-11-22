package com.zhul.erp.modules.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.framework.security.DataScopeResolver;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.finance.dto.ReceivableVO;
import com.zhul.erp.modules.inquiry.support.InquiryLookups;
import com.zhul.erp.modules.sales.constants.SalesConstants;
import com.zhul.erp.modules.sales.entity.ProformaInvoiceDO;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.repository.ProformaInvoiceMapper;
import com.zhul.erp.modules.sales.repository.SalesOrderMapper;
import com.zhul.erp.modules.sales.support.PiStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 财务管理 → 应收管理（spec finance/accounts-receivable），只读 */
@Service
@RequiredArgsConstructor
public class ReceivableService {

    private final SalesOrderMapper orderMapper;
    private final ProformaInvoiceMapper piMapper;
    private final InquiryLookups lookups;
    private final DataScopeResolver dataScopeResolver;

    public ReceivableVO overview() {
        return overview(LocalDate.now());
    }

    ReceivableVO overview(LocalDate today) {
        LambdaQueryWrapper<SalesOrderDO> w = new LambdaQueryWrapper<SalesOrderDO>()
                .eq(SalesOrderDO::getTenantId, PiStore.tenantId())
                .eq(SalesOrderDO::getStatus, SalesConstants.SO_ACTIVE)
                .ne(SalesOrderDO::getSource, SalesConstants.SO_REPLACEMENT)
                .isNull(SalesOrderDO::getDeletedAt);
        dataScopeResolver.current().apply(w, SalesOrderDO::getOwnerId);
        List<SalesOrderDO> orders = orderMapper.selectList(w);
        // 从形式发票转来的订单收款记在 PI 上（与订单详情、收款单同口径）
        List<Long> piIds = orders.stream().map(SalesOrderDO::getPiId).filter(id -> id != null && id > 0).distinct().toList();
        Map<Long, ProformaInvoiceDO> pis = piIds.isEmpty() ? Map.of() : piMapper.selectBatchIds(piIds).stream()
                .collect(Collectors.toMap(ProformaInvoiceDO::getId, Function.identity()));

        ReceivableVO vo = new ReceivableVO();
        LocalDate week = today.plusDays(7);
        LocalDate monthEnd = today.with(TemporalAdjusters.lastDayOfMonth());
        for (SalesOrderDO o : orders) {
            ProformaInvoiceDO pi = o.getPiId() == null || o.getPiId() <= 0 ? null : pis.get(o.getPiId());
            Integer status = pi != null ? pi.getReceiptStatus() : o.getReceiptStatus();
            if (Objects.equals(status, SalesConstants.RECEIPT_PAID) || Objects.equals(status, SalesConstants.RECEIPT_FREE)) {
                continue;
            }
            BigDecimal received = nz(pi != null ? pi.getReceivedAmount() : o.getReceivedAmount());
            BigDecimal feeDiff = nz(pi != null ? pi.getFeeDiffAmount() : o.getFeeDiffAmount());
            BigDecimal remaining = nz(o.getTotalAmount()).subtract(received).subtract(feeDiff);
            if (remaining.signum() <= 0) {
                continue;
            }
            ReceivableVO.Row r = new ReceivableVO.Row();
            r.setOrderId(o.getId());
            r.setSoNo(o.getSoNo());
            r.setPiId(pi == null ? null : pi.getId());
            r.setPiNo(pi == null ? null : pi.getPiNo());
            r.setSalesDate(o.getSalesDate());
            r.setCustomerId(o.getCustomerId());
            r.setOwnerId(o.getOwnerId());
            r.setCurrencyCode(o.getCurrencyCode());
            r.setTotalAmount(o.getTotalAmount());
            r.setReceivedAmount(received);
            r.setRemainingAmount(remaining);
            r.setRemainingCny(cny(remaining, o));
            LocalDate due = o.getPaymentDueDate();
            r.setDueDate(due);
            if (due != null && due.isBefore(today)) {
                r.setOverdueDays(ChronoUnit.DAYS.between(due, today));
            } else if (due != null) {
                r.setDueInDays(ChronoUnit.DAYS.between(today, due));
            }
            vo.getRows().add(r);

            ReceivableVO.Stats s = vo.getStats();
            s.setTotalCny(s.getTotalCny().add(r.getRemainingCny()));
            if (r.getOverdueDays() != null) {
                s.setOverdueCny(s.getOverdueCny().add(r.getRemainingCny()));
                s.setOverdueOrders(s.getOverdueOrders() + 1);
            } else if (due != null) {
                if (!due.isAfter(week)) {
                    s.setWeekCny(s.getWeekCny().add(r.getRemainingCny()));
                    s.setWeekOrders(s.getWeekOrders() + 1);
                }
                if (!due.isAfter(monthEnd)) {
                    s.setMonthCny(s.getMonthCny().add(r.getRemainingCny()));
                    s.setMonthOrders(s.getMonthOrders() + 1);
                }
            }
        }
        vo.getRows().sort(Comparator.comparing(ReceivableVO.Row::getDueDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ReceivableVO.Row::getSoNo, Comparator.nullsLast(Comparator.naturalOrder())));
        Set<Long> customerIds = new HashSet<>();
        List<Long> ownerIds = new ArrayList<>();
        vo.getRows().forEach(r -> {
            customerIds.add(r.getCustomerId());
            ownerIds.add(r.getOwnerId());
        });
        vo.getStats().setOrders(vo.getRows().size());
        vo.getStats().setCustomers(customerIds.size());
        Map<Long, CustomerDO> customers = lookups.customers(customerIds);
        Map<Long, String> users = lookups.userNames(ownerIds);
        for (ReceivableVO.Row r : vo.getRows()) {
            CustomerDO c = customers.get(r.getCustomerId());
            r.setCustomerName(InquiryLookups.customerName(c));
            r.setCustomerContact(InquiryLookups.contactName(c));
            r.setCustomerCountry(c == null ? null : c.getCountry());
            r.setOwnerName(users.get(r.getOwnerId()));
        }
        return vo;
    }

    /** 折人民币：未收 × 订单汇率，HALF_UP 2 位；人民币订单或没有汇率时按 1 */
    static BigDecimal cny(BigDecimal amount, SalesOrderDO o) {
        BigDecimal rate = "CNY".equalsIgnoreCase(o.getCurrencyCode()) || o.getExchangeRate() == null
                || o.getExchangeRate().signum() <= 0 ? BigDecimal.ONE : o.getExchangeRate();
        return amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
