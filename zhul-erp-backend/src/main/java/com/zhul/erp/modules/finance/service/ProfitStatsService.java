package com.zhul.erp.modules.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.finance.dto.OrderCostVO;
import com.zhul.erp.modules.finance.dto.ProfitStatsQuery;
import com.zhul.erp.modules.finance.dto.ProfitStatsVO;
import com.zhul.erp.modules.finance.support.OrderCostCalc;
import com.zhul.erp.modules.inquiry.constants.InquiryConstants;
import com.zhul.erp.modules.inquiry.support.InquiryLookups;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.sales.constants.SalesConstants;
import com.zhul.erp.modules.sales.entity.PaymentReceiptDO;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.repository.PaymentReceiptMapper;
import com.zhul.erp.modules.sales.repository.SalesOrderMapper;
import com.zhul.erp.modules.sales.support.PiStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 利润统计（spec finance/profit-stats）：本租户进行中与已完成的订单，按销售日期筛选，口径与订单成本明细一致。
 * 毛利率 = 毛利合计 ÷ 收入合计；收入按收款记录的付款方式拆线上 / 线下，还没到账的订单按销售额计入「未到账」。
 * 只读，数据范围为整个租户。
 */
@Service
@RequiredArgsConstructor
public class ProfitStatsService {

    private static final MathContext MC = MathContext.DECIMAL128;

    private final SalesOrderMapper orderMapper;
    private final PaymentReceiptMapper receiptMapper;
    private final OrderCostService orderCostService;
    private final InquiryLookups lookups;

    public ProfitStatsVO stats(ProfitStatsQuery q) {
        LocalDate from = q.getDateFrom() != null ? q.getDateFrom() : LocalDate.now().withDayOfYear(1);
        LocalDate to = q.getDateTo() != null ? q.getDateTo() : LocalDate.now();
        LambdaQueryWrapper<SalesOrderDO> w = new LambdaQueryWrapper<SalesOrderDO>()
                .eq(SalesOrderDO::getTenantId, PiStore.tenantId())
                .eq(SalesOrderDO::getStatus, SalesConstants.SO_ACTIVE)
                .isNull(SalesOrderDO::getDeletedAt)
                .ge(SalesOrderDO::getSalesDate, from)
                .le(SalesOrderDO::getSalesDate, to)
                .orderByAsc(SalesOrderDO::getSalesDate)
                .orderByAsc(SalesOrderDO::getId);
        if (q.getOwnerId() != null) {
            w.eq(SalesOrderDO::getOwnerId, q.getOwnerId());
        }
        if (q.getCustomerId() != null) {
            w.eq(SalesOrderDO::getCustomerId, q.getCustomerId());
        }
        if (q.getCustomerType() != null) {
            w.eq(SalesOrderDO::getCustomerType, q.getCustomerType());
        }
        List<SalesOrderDO> orders = orderMapper.selectList(w);
        Map<Long, OrderCostVO> costs = orderCostService.of(orders);
        if (Boolean.TRUE.equals(q.getSettledOnly())) {
            orders = orders.stream().filter(o -> costs.get(o.getId()) != null && Boolean.TRUE.equals(costs.get(o.getId()).getSettled())).toList();
        }
        Map<Long, BigDecimal[]> channels = channels(orders);
        Map<Long, CustomerDO> customers = lookups.customers(orders.stream().map(SalesOrderDO::getCustomerId).distinct().toList());
        Map<Long, String> owners = lookups.userNames(orders.stream().map(SalesOrderDO::getOwnerId).filter(Objects::nonNull).distinct().toList());

        String groupBy = q.getGroupBy() == null ? "MONTH" : q.getGroupBy();
        Function<SalesOrderDO, String> keyOf = switch (groupBy) {
            case "OWNER" -> o -> String.valueOf(o.getOwnerId());
            case "CUSTOMER" -> o -> String.valueOf(o.getCustomerId());
            default -> o -> o.getSalesDate().toString().substring(0, 7);
        };
        Map<String, List<SalesOrderDO>> grouped = orders.stream().collect(Collectors.groupingBy(keyOf, LinkedHashMap::new, Collectors.toList()));
        List<ProfitStatsVO.Group> groups = new ArrayList<>();
        grouped.forEach((key, list) -> {
            ProfitStatsVO.Group g = new ProfitStatsVO.Group();
            g.setKey(key);
            SalesOrderDO first = list.get(0);
            switch (groupBy) {
                case "OWNER" -> g.setLabel(owners.getOrDefault(first.getOwnerId(), "未指定"));
                case "CUSTOMER" -> {
                    CustomerDO c = customers.get(first.getCustomerId());
                    g.setLabel(InquiryLookups.customerName(c));
                    g.setCountry(c == null ? null : c.getCountry());
                }
                default -> g.setLabel(first.getSalesDate().getYear() + " 年 " + first.getSalesDate().getMonthValue() + " 月");
            }
            g.setTotals(totals(list, costs, channels));
            g.setOrders(list.stream().map(o -> row(o, costs.get(o.getId()), customers, owners)).toList());
            groups.add(g);
        });
        if (!"MONTH".equals(groupBy)) {
            groups.sort(Comparator.comparing((ProfitStatsVO.Group g) -> g.getTotals().getProfit()).reversed());
        }
        ProfitStatsVO vo = new ProfitStatsVO();
        vo.setSummary(totals(orders, costs, channels));
        vo.setGroups(groups);
        return vo;
    }

    private static ProfitStatsVO.Totals totals(List<SalesOrderDO> orders, Map<Long, OrderCostVO> costs, Map<Long, BigDecimal[]> channels) {
        ProfitStatsVO.Totals t = new ProfitStatsVO.Totals();
        BigDecimal sales = BigDecimal.ZERO;
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        BigDecimal[] byType = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        BigDecimal online = BigDecimal.ZERO;
        BigDecimal offline = BigDecimal.ZERO;
        int unsettled = 0;
        int newCount = 0;
        for (SalesOrderDO o : orders) {
            OrderCostVO c = costs.get(o.getId());
            if (c == null) {
                continue;
            }
            sales = sales.add(nz(o.getTotalAmountCny()));
            BigDecimal net = c.getRevenue().subtract(c.getRefund());
            revenue = revenue.add(net);
            cost = cost.add(c.getCost());
            if (!Boolean.TRUE.equals(c.getSettled())) {
                unsettled++;
            }
            boolean isNew = !Objects.equals(o.getCustomerType(), InquiryConstants.CUSTOMER_RETURNING);
            int base = isNew ? 0 : 3;
            newCount += isNew ? 1 : 0;
            byType[base] = byType[base].add(net);
            byType[base + 1] = byType[base + 1].add(c.getCost());
            byType[base + 2] = byType[base + 2].add(c.getProfit());
            BigDecimal[] ch = channels.get(o.getId());
            if (ch != null && Boolean.TRUE.equals(isPaid(c))) {
                // 收入按到账毛额的线上 / 线下比例拆分
                BigDecimal sum = ch[0].add(ch[1]);
                if (sum.signum() > 0) {
                    BigDecimal on = OrderCostCalc.money(net.multiply(ch[0], MC).divide(sum, MC));
                    online = online.add(on);
                    offline = offline.add(net.subtract(on));
                }
            }
        }
        BigDecimal profit = revenue.subtract(cost);
        t.setOrderCount((int) orders.stream().filter(o -> costs.containsKey(o.getId())).count());
        t.setCustomerCount((int) orders.stream().filter(o -> costs.containsKey(o.getId())).map(SalesOrderDO::getCustomerId).distinct().count());
        t.setUnsettledCount(unsettled);
        t.setSalesAmountCny(sales);
        t.setRevenue(revenue);
        t.setCost(cost);
        t.setProfit(profit);
        t.setProfitRate(OrderCostCalc.rate(profit, revenue));
        t.setNewOrderCount(newCount);
        t.setOldOrderCount(t.getOrderCount() - newCount);
        t.setNewRevenue(byType[0]);
        t.setNewCost(byType[1]);
        t.setNewProfit(byType[2]);
        t.setOldRevenue(byType[3]);
        t.setOldCost(byType[4]);
        t.setOldProfit(byType[5]);
        t.setOnlineRevenue(online);
        t.setOfflineRevenue(offline);
        t.setUnreceivedRevenue(revenue.subtract(online).subtract(offline));
        return t;
    }

    /** 收入按毛额计时（已到账）才按到账的付款方式拆分 */
    private static Boolean isPaid(OrderCostVO c) {
        return !c.getPending().contains("货款没收齐");
    }

    /** 订单 → [线上毛额折人民币, 线下毛额折人民币] */
    private Map<Long, BigDecimal[]> channels(List<SalesOrderDO> orders) {
        Map<Long, BigDecimal[]> out = new HashMap<>();
        if (orders.isEmpty()) {
            return out;
        }
        Map<Long, Long> soByPi = new HashMap<>();
        orders.stream().filter(o -> o.getPiId() != null).forEach(o -> soByPi.put(o.getPiId(), o.getId()));
        List<Long> soIds = orders.stream().map(SalesOrderDO::getId).toList();
        for (PaymentReceiptDO r : receiptMapper.selectList(new LambdaQueryWrapper<PaymentReceiptDO>()
                .eq(PaymentReceiptDO::getKind, SalesConstants.KIND_RECEIPT)
                .eq(PaymentReceiptDO::getStatus, SalesConstants.RECORD_VALID)
                .isNull(PaymentReceiptDO::getDeletedAt)
                .and(x -> {
                    x.in(PaymentReceiptDO::getSoId, soIds);
                    if (!soByPi.isEmpty()) {
                        x.or().in(PaymentReceiptDO::getPiId, soByPi.keySet());
                    }
                }))) {
            Long so = r.getPiId() != null && soByPi.containsKey(r.getPiId()) ? soByPi.get(r.getPiId()) : r.getSoId();
            if (so == null) {
                continue;
            }
            BigDecimal[] ch = out.computeIfAbsent(so, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            int idx = Objects.equals(r.getChannel(), SalesConstants.CHANNEL_ONLINE) ? 0 : 1;
            ch[idx] = ch[idx].add(nz(r.getAmountCny()));
        }
        return out;
    }

    private static ProfitStatsVO.OrderRow row(SalesOrderDO o, OrderCostVO c, Map<Long, CustomerDO> customers, Map<Long, String> owners) {
        ProfitStatsVO.OrderRow r = new ProfitStatsVO.OrderRow();
        r.setSoId(o.getId());
        r.setSoNo(o.getSoNo());
        r.setCustomerName(InquiryLookups.customerName(customers.get(o.getCustomerId())));
        r.setCustomerType(o.getCustomerType());
        r.setSalesDate(o.getSalesDate());
        r.setOwnerName(owners.get(o.getOwnerId()));
        if (c != null) {
            r.setRevenue(c.getRevenue().subtract(c.getRefund()));
            r.setCost(c.getCost());
            r.setProfit(c.getProfit());
            r.setProfitRate(c.getProfitRate());
            r.setSettled(c.getSettled());
            r.setPending(c.getPending());
        }
        return r;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
