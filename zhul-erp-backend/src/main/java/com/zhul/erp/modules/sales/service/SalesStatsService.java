package com.zhul.erp.modules.sales.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.zhul.erp.framework.security.DataScope;
import com.zhul.erp.framework.security.DataScopeResolver;
import com.zhul.erp.modules.crm.entity.OpportunityDO;
import com.zhul.erp.modules.crm.repository.OpportunityMapper;
import com.zhul.erp.modules.finance.dto.OrderCostVO;
import com.zhul.erp.modules.finance.service.OrderCostService;
import com.zhul.erp.modules.inquiry.constants.InquiryConstants;
import com.zhul.erp.modules.inquiry.customerinquiry.entity.CustomerInquiryDO;
import com.zhul.erp.modules.inquiry.customerinquiry.repository.CustomerInquiryMapper;
import com.zhul.erp.modules.inquiry.support.InquiryLookups;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.quotation.constants.QuotationConstants;
import com.zhul.erp.modules.quotation.entity.QuotationDO;
import com.zhul.erp.modules.quotation.repository.QuotationMapper;
import com.zhul.erp.modules.sales.constants.SalesConstants;
import com.zhul.erp.modules.sales.dto.SalesStatsQuery;
import com.zhul.erp.modules.sales.dto.SalesStatsVO;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 销售统计（spec sales/sales-stats）：各指标按各自单据的日期归入期间
 * （商机按创建、询盘按询盘日期、报价与 PI 按发送、订单按销售日期），
 * 业务员取单据负责人，数据范围遵循销售订单的数据权限；
 * 收入与毛利复用 {@link OrderCostService}（与利润统计一致，收入扣退款）。
 * 报价单与 PI 的新老客户：发送日期之前该客户已有有效订单为老客户。只读。
 */
@Service
@RequiredArgsConstructor
public class SalesStatsService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final OpportunityMapper opportunityMapper;
    private final CustomerInquiryMapper inquiryMapper;
    private final QuotationMapper quotationMapper;
    private final ProformaInvoiceMapper piMapper;
    private final SalesOrderMapper orderMapper;
    private final OrderCostService orderCostService;
    private final DataScopeResolver dataScopeResolver;
    private final InquiryLookups lookups;

    /** 一组的累计：0 全部、1 新客户、2 老客户 */
    private static final class Acc {
        private int opportunities;
        private final int[] inquiries = new int[3];
        private final int[] quotations = new int[3];
        private final int[] intents = new int[3];
        private final BigDecimal[] intentAmount = zeros();
        private final int[] pending = new int[3];
        private final BigDecimal[] pendingAmount = zeros();
        private final int[] orders = new int[3];
        private final BigDecimal[] revenue = zeros();
        private final BigDecimal[] profit = zeros();
        /** 客户 → 期间内首张订单是否老客户 */
        private final Map<Long, Boolean> customers = new LinkedHashMap<>();

        private static BigDecimal[] zeros() {
            return new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        }

        private static void count(int[] a, boolean old) {
            a[0]++;
            a[old ? 2 : 1]++;
        }

        private static void add(BigDecimal[] a, BigDecimal v, boolean old) {
            BigDecimal x = v == null ? BigDecimal.ZERO : v;
            a[0] = a[0].add(x);
            a[old ? 2 : 1] = a[old ? 2 : 1].add(x);
        }
    }

    public SalesStatsVO stats(SalesStatsQuery q) {
        LocalDate from = q.getDateFrom() != null ? q.getDateFrom() : LocalDate.now().withDayOfYear(1);
        LocalDate to = q.getDateTo() != null ? q.getDateTo() : LocalDate.now();
        String view = q.getView() == null ? "MONTH" : q.getView();
        DataScope scope = dataScopeResolver.current();
        int tenant = PiStore.tenantId();

        List<OpportunityDO> opps = opportunityMapper.selectList(owner(scope, q.getOwnerId(),
                new LambdaQueryWrapper<OpportunityDO>()
                .eq(OpportunityDO::getTenantId, tenant)
                .isNull(OpportunityDO::getDeletedAt)
                .ge(OpportunityDO::getCreateTime, from.atStartOfDay())
                .lt(OpportunityDO::getCreateTime, to.plusDays(1).atStartOfDay()), OpportunityDO::getOwnerId));
        List<CustomerInquiryDO> inquiries = inquiryMapper.selectList(owner(scope, q.getOwnerId(),
                new LambdaQueryWrapper<CustomerInquiryDO>()
                .eq(CustomerInquiryDO::getTenantId, tenant)
                .isNull(CustomerInquiryDO::getDeletedAt)
                .ne(CustomerInquiryDO::getStatus, InquiryConstants.STATUS_CANCELLED)
                .ge(CustomerInquiryDO::getInquiryDate, from)
                .le(CustomerInquiryDO::getInquiryDate, to), CustomerInquiryDO::getOwnerId));
        List<QuotationDO> quotations = quotationMapper.selectList(owner(scope, q.getOwnerId(),
                new LambdaQueryWrapper<QuotationDO>()
                .eq(QuotationDO::getTenantId, tenant)
                .isNull(QuotationDO::getDeletedAt)
                .ne(QuotationDO::getStatus, QuotationConstants.STATUS_VOID)
                .isNotNull(QuotationDO::getSentAt)
                .ge(QuotationDO::getSentAt, from.atStartOfDay())
                .lt(QuotationDO::getSentAt, to.plusDays(1).atStartOfDay()), QuotationDO::getOwnerId));
        List<ProformaInvoiceDO> pis = piMapper.selectList(owner(scope, q.getOwnerId(),
                new LambdaQueryWrapper<ProformaInvoiceDO>()
                .eq(ProformaInvoiceDO::getTenantId, tenant)
                .isNull(ProformaInvoiceDO::getDeletedAt)
                .ne(ProformaInvoiceDO::getStatus, SalesConstants.PI_VOID)
                .isNotNull(ProformaInvoiceDO::getSentAt)
                .ge(ProformaInvoiceDO::getSentAt, from.atStartOfDay())
                .lt(ProformaInvoiceDO::getSentAt, to.plusDays(1).atStartOfDay()), ProformaInvoiceDO::getOwnerId));
        List<SalesOrderDO> orders = orderMapper.selectList(owner(scope, q.getOwnerId(),
                new LambdaQueryWrapper<SalesOrderDO>()
                .eq(SalesOrderDO::getTenantId, tenant)
                .isNull(SalesOrderDO::getDeletedAt)
                .eq(SalesOrderDO::getStatus, SalesConstants.SO_ACTIVE)
                .ge(SalesOrderDO::getSalesDate, from)
                .le(SalesOrderDO::getSalesDate, to)
                .orderByAsc(SalesOrderDO::getSalesDate)
                .orderByAsc(SalesOrderDO::getId), SalesOrderDO::getOwnerId));
        Map<Long, OrderCostVO> costs = orderCostService.of(orders);
        Map<Long, LocalDate> firstOrder = firstOrderDates(Stream.concat(
                quotations.stream().map(QuotationDO::getCustomerId),
                pis.stream().map(ProformaInvoiceDO::getCustomerId)).toList());

        Function<LocalDate, String> monthKey = d -> d.toString().substring(0, 7);
        boolean byOwner = "OWNER".equals(view);
        Map<String, Acc> groups = byOwner ? new LinkedHashMap<>() : new TreeMap<>();
        Acc total = new Acc();
        for (OpportunityDO o : opps) {
            String k = byOwner ? key(o.getOwnerId()) : monthKey.apply(o.getCreateTime().toLocalDate());
            for (Acc a : accs(groups, total, k)) {
                a.opportunities++;
            }
        }
        for (CustomerInquiryDO i : inquiries) {
            boolean old = Objects.equals(i.getCustomerType(), InquiryConstants.CUSTOMER_RETURNING);
            for (Acc a : accs(groups, total, byOwner ? key(i.getOwnerId()) : monthKey.apply(i.getInquiryDate()))) {
                Acc.count(a.inquiries, old);
            }
        }
        for (QuotationDO x : quotations) {
            boolean old = isOld(firstOrder, x.getCustomerId(), x.getSentAt().toLocalDate());
            String k = byOwner ? key(x.getOwnerId()) : monthKey.apply(x.getSentAt().toLocalDate());
            for (Acc a : accs(groups, total, k)) {
                Acc.count(a.quotations, old);
            }
        }
        for (ProformaInvoiceDO p : pis) {
            boolean old = isOld(firstOrder, p.getCustomerId(), p.getSentAt().toLocalDate());
            boolean pending = !Objects.equals(p.getReceiptStatus(), SalesConstants.RECEIPT_PAID)
                    && !Objects.equals(p.getStatus(), SalesConstants.PI_CLOSED);
            String k = byOwner ? key(p.getOwnerId()) : monthKey.apply(p.getSentAt().toLocalDate());
            for (Acc a : accs(groups, total, k)) {
                Acc.count(a.intents, old);
                Acc.add(a.intentAmount, p.getTotalAmountCny(), old);
                if (pending) {
                    Acc.count(a.pending, old);
                    Acc.add(a.pendingAmount, p.getTotalAmountCny(), old);
                }
            }
        }
        for (SalesOrderDO o : orders) {
            boolean old = Objects.equals(o.getCustomerType(), InquiryConstants.CUSTOMER_RETURNING);
            OrderCostVO c = costs.get(o.getId());
            for (Acc a : accs(groups, total, byOwner ? key(o.getOwnerId()) : monthKey.apply(o.getSalesDate()))) {
                Acc.count(a.orders, old);
                if (c != null) {
                    Acc.add(a.revenue, c.getRevenue().subtract(c.getRefund()), old);
                    Acc.add(a.profit, c.getProfit(), old);
                }
                a.customers.putIfAbsent(o.getCustomerId(), old);
            }
        }

        Map<Long, String> owners = byOwner
                ? lookups.userNames(groups.keySet().stream().filter(k -> !k.isEmpty()).map(Long::valueOf).toList())
                : Map.of();
        SalesStatsVO vo = new SalesStatsVO();
        List<SalesStatsVO.Row> rows = new ArrayList<>();
        groups.forEach((k, a) -> rows.add(row(k, byOwner ? ownerLabel(owners, k) : monthLabel(k), a)));
        if (byOwner) {
            rows.sort(Comparator.comparing((SalesStatsVO.Row r) -> r.getRevenue().getAll()).reversed());
        }
        vo.setRows(rows);
        vo.setTotal(row("", "合计", total));
        vo.setCustomers("CUSTOMER".equals(view) ? customerRows(orders, costs) : List.of());
        return vo;
    }

    // ---------------------------------------------------------------- 组装

    private static SalesStatsVO.Row row(String key, String label, Acc a) {
        SalesStatsVO.Row r = new SalesStatsVO.Row();
        r.setKey(key);
        r.setLabel(label);
        r.setOpportunities(a.opportunities);
        r.setInquiries(split(a.inquiries));
        r.setQuotations(split(a.quotations));
        r.setIntents(split(a.intents));
        r.setIntentAmount(money(a.intentAmount));
        r.setPending(split(a.pending));
        r.setPendingAmount(money(a.pendingAmount));
        r.setOrders(split(a.orders));
        int[] customers = new int[3];
        a.customers.values().forEach(old -> Acc.count(customers, old));
        r.setCustomers(split(customers));
        r.setRevenue(money(a.revenue));
        r.setProfit(money(a.profit));
        r.setProfitRate(ratio(a.profit[0], a.revenue[0]));
        r.setProfitRateNew(ratio(a.profit[1], a.revenue[1]));
        r.setProfitRateOld(ratio(a.profit[2], a.revenue[2]));
        r.setInquiryRate(ratio(a.inquiries[0], a.opportunities));
        r.setInquiryRateNew(ratio(a.inquiries[1], a.opportunities));
        r.setOrderRate(ratio(a.orders[0], a.inquiries[0]));
        r.setOrderRateNew(ratio(a.orders[1], a.inquiries[1]));
        r.setOrderRateOld(ratio(a.orders[2], a.inquiries[2]));
        r.setOverallRate(ratio(a.orders[0], a.opportunities));
        r.setAvgPrice(avg(a.revenue[0], a.orders[0]));
        r.setAvgPriceNew(avg(a.revenue[1], a.orders[1]));
        r.setAvgPriceOld(avg(a.revenue[2], a.orders[2]));
        r.setOrderRepeatRate(ratio(a.orders[2], a.orders[0]));
        r.setCustomerRepeatRate(ratio(customers[2], customers[0]));
        return r;
    }

    private List<SalesStatsVO.CustomerRow> customerRows(List<SalesOrderDO> orders, Map<Long, OrderCostVO> costs) {
        Map<Long, List<SalesOrderDO>> byCustomer = orders.stream()
                .collect(Collectors.groupingBy(SalesOrderDO::getCustomerId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, CustomerDO> customers = lookups.customers(byCustomer.keySet());
        List<SalesStatsVO.CustomerRow> out = new ArrayList<>();
        byCustomer.forEach((id, list) -> {
            BigDecimal revenue = BigDecimal.ZERO;
            BigDecimal profit = BigDecimal.ZERO;
            for (SalesOrderDO o : list) {
                OrderCostVO c = costs.get(o.getId());
                if (c != null) {
                    revenue = revenue.add(c.getRevenue().subtract(c.getRefund()));
                    profit = profit.add(c.getProfit());
                }
            }
            CustomerDO c = customers.get(id);
            SalesStatsVO.CustomerRow r = new SalesStatsVO.CustomerRow();
            r.setCustomerId(id);
            r.setCustomerName(InquiryLookups.customerName(c));
            r.setCountry(c == null ? null : c.getCountry());
            r.setCustomerType(list.get(0).getCustomerType());
            r.setOrders(list.size());
            r.setRevenue(revenue.setScale(2, RoundingMode.HALF_UP));
            r.setProfit(profit.setScale(2, RoundingMode.HALF_UP));
            r.setProfitRate(ratio(profit, revenue));
            out.add(r);
        });
        out.sort(Comparator.comparing(SalesStatsVO.CustomerRow::getRevenue).reversed());
        return out;
    }

    // ---------------------------------------------------------------- 工具

    private static <T> LambdaQueryWrapper<T> owner(DataScope scope, Long ownerId, LambdaQueryWrapper<T> w,
                                                   SFunction<T, ?> column) {
        if (ownerId != null) {
            w.eq(column, ownerId);
        }
        return scope.apply(w, column);
    }

    private static String ownerLabel(Map<Long, String> owners, String key) {
        return key.isEmpty() ? "未指定" : owners.getOrDefault(Long.valueOf(key), key);
    }

    private static String monthLabel(String key) {
        return Integer.parseInt(key.substring(0, 4)) + " 年 " + Integer.parseInt(key.substring(5)) + " 月";
    }

    private static String key(Long ownerId) {
        return ownerId == null ? "" : String.valueOf(ownerId);
    }

    private static List<Acc> accs(Map<String, Acc> groups, Acc total, String key) {
        return List.of(groups.computeIfAbsent(key, k -> new Acc()), total);
    }

    /** 客户 → 最早的有效订单销售日期 */
    private Map<Long, LocalDate> firstOrderDates(Collection<Long> customerIds) {
        List<Long> ids = customerIds.stream().filter(Objects::nonNull).distinct().toList();
        Map<Long, LocalDate> out = new HashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        orderMapper.selectList(new LambdaQueryWrapper<SalesOrderDO>()
                        .select(SalesOrderDO::getCustomerId, SalesOrderDO::getSalesDate)
                        .eq(SalesOrderDO::getTenantId, PiStore.tenantId())
                        .eq(SalesOrderDO::getStatus, SalesConstants.SO_ACTIVE)
                        .isNull(SalesOrderDO::getDeletedAt)
                        .in(SalesOrderDO::getCustomerId, ids))
                .forEach(o -> out.merge(o.getCustomerId(), o.getSalesDate(), (a, b) -> a.isBefore(b) ? a : b));
        return out;
    }

    private static boolean isOld(Map<Long, LocalDate> firstOrder, Long customerId, LocalDate date) {
        LocalDate first = customerId == null ? null : firstOrder.get(customerId);
        return first != null && first.isBefore(date);
    }

    private static SalesStatsVO.Split split(int[] a) {
        SalesStatsVO.Split s = new SalesStatsVO.Split();
        s.setAll(a[0]);
        s.setFresh(a[1]);
        s.setOld(a[2]);
        return s;
    }

    private static SalesStatsVO.Money money(BigDecimal[] a) {
        SalesStatsVO.Money m = new SalesStatsVO.Money();
        m.setAll(a[0].setScale(2, RoundingMode.HALF_UP));
        m.setFresh(a[1].setScale(2, RoundingMode.HALF_UP));
        m.setOld(a[2].setScale(2, RoundingMode.HALF_UP));
        return m;
    }

    private static BigDecimal ratio(int numerator, int denominator) {
        return denominator == 0 ? null
                : BigDecimal.valueOf(numerator).multiply(HUNDRED)
                        .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        return denominator == null || denominator.signum() == 0 ? null
                : numerator.multiply(HUNDRED).divide(denominator, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal avg(BigDecimal amount, int count) {
        return count == 0 ? null : amount.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }
}
