package com.zhul.erp.modules.workbench.widget;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.framework.security.DataScopeResolver;
import com.zhul.erp.framework.security.PermissionChecker;
import com.zhul.erp.modules.finance.dto.FxPendingVO;
import com.zhul.erp.modules.finance.dto.OrderCostVO;
import com.zhul.erp.modules.finance.dto.OperatingReportVO;
import com.zhul.erp.modules.finance.dto.PayableStatsVO;
import com.zhul.erp.modules.finance.dto.ReceivableVO;
import com.zhul.erp.modules.finance.service.FxSettlementService;
import com.zhul.erp.modules.finance.service.OrderCostService;
import com.zhul.erp.modules.finance.service.OperatingReportService;
import com.zhul.erp.modules.finance.service.PayableQueryService;
import com.zhul.erp.modules.finance.service.ReceivableService;
import com.zhul.erp.modules.sales.constants.SalesConstants;
import com.zhul.erp.modules.sales.dto.OverduePiVO;
import com.zhul.erp.modules.sales.dto.SalesStatsVO;
import com.zhul.erp.modules.sales.entity.ProformaInvoiceDO;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.repository.ProformaInvoiceMapper;
import com.zhul.erp.modules.sales.repository.SalesOrderMapper;
import com.zhul.erp.modules.sales.service.PiService;
import com.zhul.erp.modules.sales.service.SalesOrderService;
import com.zhul.erp.modules.sales.service.SalesStatsService;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.warehouse.service.DiscrepancyService;
import com.zhul.erp.modules.warehouse.service.ShipmentService;
import com.zhul.erp.modules.workbench.dto.WidgetData;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 老板看板（spec「老板看板」）：经营概览、资金、销售漏斗、近 12 个月趋势、排行、风险提醒。
 * 口径同经营统计、销售统计、应收管理、应付单、结汇单页；数据范围按当前用户的角色（老板通常为全部）。
 */
@Configuration
public class BossWidgets {

    static final String OPERATING = "/finance/operating-report";
    static final String PROFIT = "/finance/profit-stats";
    static final String FX = "/finance/fx-settlements";
    static final String RECEIVABLES = "/finance/receivables";

    @Bean
    public WorkbenchWidget bizOverviewWidget(OperatingReportService report, SalesOrderService orders) {
        return W.widget("biz-overview", List.of(OPERATING, PROFIT), () -> {
            LocalDate today = LocalDate.now();
            OperatingReportVO.Row cur = month(report, today);
            OperatingReportVO.Row prev = month(report, today.minusMonths(1));
            BigDecimal received = orders.receivedNetCny(W.monthStart(), today);
            BigDecimal receivedPrev = orders.receivedNetCny(W.lastMonthStart(), W.lastMonthEnd());
            return W.of(W.metrics(
                    W.link(W.compare("成交额", revenue(cur), revenue(prev), "money", true), OPERATING),
                    W.link(W.compare("毛利", profit(cur), profit(prev), "money", true), PROFIT),
                    W.compare("毛利率", cur == null ? null : cur.getProfitRate(), prev == null ? null : prev.getProfitRate(),
                            "percent", true),
                    W.compare("实收回款", received, receivedPrev, "money", true),
                    W.compare("费用", cur == null ? BigDecimal.ZERO : W.num(cur.getOpexTotal()),
                            prev == null ? BigDecimal.ZERO : W.num(prev.getOpexTotal()), "money", false)));
        });
    }

    @Bean
    public WorkbenchWidget cashWidget(PermissionChecker perm, DataScopeResolver scope, ProformaInvoiceMapper pis,
                                      SalesOrderMapper orders, ReceivableService receivables, PayableQueryService payables,
                                      FxSettlementService fx) {
        return W.widget("cash", List.of(RECEIVABLES, PurchaseLeadWidgets.PAYABLES, FX), () -> {
            List<WidgetData.Metric> items = new ArrayList<>();
            if (perm.canAccessMenu(RECEIVABLES)) {
                // 与应收管理同口径、同数（ui-redesign D32）：有效销售订单还没收齐的剩余，按订单汇率折人民币
                ReceivableVO.Stats s = receivables.overview().getStats();
                String hint;
                String tone = null;
                if (s.getOverdueCny().signum() > 0) {
                    hint = "其中已逾期 CNY " + money(s.getOverdueCny());
                    tone = "red";
                } else {
                    // 已发出、还没转成订单的 PI 不在应收管理里，单独说一句
                    int[] count = {0};
                    BigDecimal piLeft = perm.canAccessMenu(SalesWidgets.PIS) ? unconvertedPiLeft(pis, orders, scope, count)
                            : BigDecimal.ZERO;
                    hint = count[0] > 0 ? "另有 " + count[0] + " 张 PI 待收 CNY " + money(piLeft) : "没有逾期";
                }
                items.add(W.link(W.tone(W.metric("应收未收", s.getTotalCny().setScale(2, RoundingMode.HALF_UP), "money", hint),
                        tone), RECEIVABLES));
            }
            if (perm.canAccessMenu(PurchaseLeadWidgets.PAYABLES)) {
                PayableStatsVO s = payables.stats();
                boolean overdue = s.getOverdueCount() > 0;
                // 已逾期统一用错误色（ui-redesign S5）
                items.add(W.link(W.tone(W.metric("本月到期应付", s.getMonthDue(), "money",
                        overdue ? "其中已逾期 CNY " + money(s.getOverdue()) : "没有逾期"),
                        overdue ? "red" : null), PurchaseLeadWidgets.PAYABLES));
            }
            WidgetData.Block tags = null;
            if (perm.canAccessMenu(FX)) {
                List<FxPendingVO> pending = fx.pending();
                tags = W.note(pending.isEmpty() ? "未结汇外币：没有" : "未结汇外币");
                tags.setTags(pending.stream()
                        .map(p -> p.getCurrencyCode() + " " + p.getBalance().setScale(2, RoundingMode.HALF_UP).toPlainString())
                        .toList());
            }
            return W.of(items.isEmpty() ? null : W.metrics(items), tags);
        });
    }

    /** 已发出、未付清、还没有有效订单的 PI 剩余未收（按 PI 汇率折人民币）；count[0] 返回张数 */
    private static BigDecimal unconvertedPiLeft(ProformaInvoiceMapper pis, SalesOrderMapper orders, DataScopeResolver scope,
                                                int[] count) {
        List<ProformaInvoiceDO> unpaid = pis.selectList(SalesWidgets.unpaidPis(scope));
        Map<Long, BigDecimal> converted = activeOrderTotals(orders, unpaid);
        BigDecimal sum = BigDecimal.ZERO;
        for (ProformaInvoiceDO p : unpaid) {
            if (converted.containsKey(p.getId())) {
                continue;
            }
            BigDecimal left = W.num(p.getTotalAmount()).subtract(W.num(p.getReceivedAmount())).subtract(W.num(p.getFeeDiffAmount()));
            if (left.signum() > 0) {
                count[0]++;
                sum = sum.add(left.multiply(W.num(p.getExchangeRate())));
            }
        }
        return sum;
    }

    @Bean
    public WorkbenchWidget salesFunnelWidget(SalesStatsService stats) {
        return W.widget("sales-funnel", List.of(SalesWidgets.STATS), () -> {
            SalesStatsVO.Row t = stats.stats(SalesWidgets.query(W.monthStart(), LocalDate.now(), "MONTH")).getTotal();
            long[] values = {t.getOpportunities(), t.getInquiries().getAll(), t.getQuotations().getAll(),
                    t.getIntents().getAll(), t.getOrders().getAll()};
            String[] labels = {"新增商机", "询盘", "报价", "PI", "成交订单"};
            String[] links = {"/crm/opportunities", SalesWidgets.INQUIRIES, "/quotation/quotations", SalesWidgets.PIS,
                    SalesWidgets.ORDERS};
            List<WidgetData.Step> steps = new ArrayList<>();
            for (int i = 0; i < values.length; i++) {
                WidgetData.Step s = new WidgetData.Step();
                s.setLabel(labels[i]);
                s.setValue(BigDecimal.valueOf(values[i]));
                s.setRate(i == 0 ? null : W.rate(values[i], values[i - 1]));
                s.setLink(links[i]);
                steps.add(s);
            }
            WidgetData.Block b = new WidgetData.Block();
            b.setType("funnel");
            b.setSteps(steps);
            return W.of(b);
        });
    }

    @Bean
    public WorkbenchWidget revenueTrendWidget(OperatingReportService report) {
        return W.widget("revenue-trend", List.of(OPERATING), () -> {
            LocalDate today = LocalDate.now();
            List<OperatingReportVO.Row> rows = new ArrayList<>();
            OperatingReportVO lastYear = report.year(today.getYear() - 1, null);
            OperatingReportVO thisYear = report.year(today.getYear(), null);
            for (int i = 11; i >= 0; i--) {
                LocalDate m = today.minusMonths(i);
                OperatingReportVO vo = m.getYear() == today.getYear() ? thisYear : lastYear;
                rows.add(vo.getRows().stream().filter(r -> r.getMonth() != null && r.getMonth() == m.getMonthValue())
                        .findFirst().orElse(null));
            }
            WidgetData.Bars bars = new WidgetData.Bars();
            bars.setFormat("money");
            for (int i = 11; i >= 0; i--) {
                LocalDate m = today.minusMonths(i);
                OperatingReportVO.Row r = rows.get(11 - i);
                bars.getLabels().add(m.getMonthValue() + "月");
                bars.getValues().add(revenue(r));
            }
            // 主角卡（ui-redesign D28）：本月成交额与较上月，柱图下一句结论
            OperatingReportVO.Row last = rows.get(11);
            BigDecimal cur = revenue(last);
            BigDecimal sum = bars.getValues().stream().map(W::num).reduce(BigDecimal.ZERO, BigDecimal::add);
            boolean highest = cur.signum() > 0
                    && bars.getValues().stream().map(W::num).allMatch(v -> v.compareTo(cur) <= 0);
            String text = (highest ? "本月是近 12 个月最高 · " : "") + "近 12 个月合计 CNY " + money(sum);
            return W.of(W.metrics(W.link(W.compare("成交额", cur, revenue(rows.get(10)), "money", true), OPERATING)),
                    W.bars(bars, text));
        });
    }

    @Bean
    public WorkbenchWidget riskAlertsWidget(PermissionChecker perm, PiService piService, ShipmentService shipments,
                                            DiscrepancyService discrepancies, DataScopeResolver scope, SalesOrderMapper orders,
                                            OrderCostService orderCost) {
        return W.widget("risk-alerts", List.of(SalesWidgets.PIS, PurchaserWidgets.SHIPMENTS, SalesWidgets.ORDERS), () -> {
            List<WidgetData.Todo> todos = new ArrayList<>();
            if (perm.canAccessMenu(SalesWidgets.PIS)) {
                OverduePiVO o = piService.overdue();
                todos.add(W.todo("file-clock", "red", "过期未收款 PI", "已过有效期仍未收到款", o.getCount() == null ? 0 : o.getCount(),
                        true, SalesWidgets.PIS + "?expired=1"));
            }
            if (perm.canAccessMenu(PurchaserWidgets.SHIPMENTS)) {
                long late = shipments.countScopedInTransit().overdue();
                todos.add(W.todo("truck", "orange", "超期未到货", "在途且已过预计到货日期", late, true, PurchaserWidgets.SHIPMENTS));
                todos.add(W.todo("warning", "orange", "未处理的到货差异", "少发、错发、不良待处理", discrepancies.countPending(), true,
                        PurchaserWidgets.SHIPMENTS + "?tab=diffs"));
            }
            if (perm.canAccessMenu(SalesWidgets.ORDERS)) {
                LambdaQueryWrapper<SalesOrderDO> w = new LambdaQueryWrapper<SalesOrderDO>()
                        .eq(SalesOrderDO::getTenantId, PiStore.tenantId())
                        .eq(SalesOrderDO::getStatus, SalesConstants.SO_ACTIVE)
                        .ge(SalesOrderDO::getSalesDate, W.monthStart())
                        .isNull(SalesOrderDO::getDeletedAt);
                scope.current().apply(w, SalesOrderDO::getOwnerId);
                // 与订单列表「毛利」列、「毛利为负」筛选同一口径：按实际成本读时计算（audit BUS-070）
                List<SalesOrderDO> month = orders.selectList(w);
                Map<Long, OrderCostVO> costs = month.isEmpty() ? Map.of() : orderCost.of(month);
                long loss = month.stream().map(o -> costs.get(o.getId()))
                        .filter(c -> c != null && c.getProfit() != null && c.getProfit().signum() < 0).count();
                todos.add(W.todo("down", "red", "毛利为负的订单", "本月成交里亏本的订单", loss, true,
                        SalesWidgets.ORDERS + "?loss=1&dateFrom=" + W.monthStart() + "&dateTo=" + LocalDate.now()));
            }
            return W.of(W.todos(todos));
        });
    }

    @Bean
    public WorkbenchWidget rankingsWidget(SalesStatsService stats) {
        return W.widget("rankings", List.of(SalesWidgets.STATS), () -> {
            SalesStatsVO owners = stats.stats(SalesWidgets.query(W.monthStart(), LocalDate.now(), "OWNER"));
            WidgetData.Table t1 = new WidgetData.Table();
            t1.getColumns().add(WidgetData.Column.of("业务员", "l", null));
            t1.getColumns().add(WidgetData.Column.of("成交额", "r", "money"));
            t1.getColumns().add(WidgetData.Column.of("毛利率", "r", "percent"));
            t1.setEmpty("本月还没有成交");
            owners.getRows().stream()
                    .filter(r -> r.getRevenue() != null && W.num(r.getRevenue().getAll()).signum() > 0)
                    .sorted(Comparator.comparing((SalesStatsVO.Row r) -> W.num(r.getRevenue().getAll())).reversed())
                    .limit(5)
                    .forEach(r -> t1.getRows().add(List.of(r.getLabel(), W.num(r.getRevenue().getAll()),
                            r.getProfitRate() == null ? "—" : r.getProfitRate())));
            SalesStatsVO customers = stats.stats(SalesWidgets.query(W.monthStart(), LocalDate.now(), "CUSTOMER"));
            WidgetData.Table t2 = new WidgetData.Table();
            t2.getColumns().add(WidgetData.Column.of("客户", "l", null));
            t2.getColumns().add(WidgetData.Column.of("成交额", "r", "money"));
            t2.getColumns().add(WidgetData.Column.of("订单", "r", "count"));
            t2.setEmpty("本月还没有成交");
            t2.setCustomers(new ArrayList<>());
            (customers.getCustomers() == null ? List.<SalesStatsVO.CustomerRow>of() : customers.getCustomers()).stream()
                    .sorted(Comparator.comparing((SalesStatsVO.CustomerRow r) -> W.num(r.getRevenue())).reversed())
                    .limit(5)
                    .forEach(r -> {
                        t2.getRows().add(List.of(r.getCustomerName(), W.num(r.getRevenue()), r.getOrders()));
                        t2.getCustomers().add(WidgetData.Customer.of(r.getCustomerName(), r.getCountry(), r.getCustomerContact()));
                    });
            return W.of(W.table("业务员 · 本月成交额", t1), W.table("客户 · 本月成交额", t2));
        });
    }

    // ---------------------------------------------------------------- 工具

    /** 经营统计里某个月的一行；没有（如未来月份）为空 */
    private static OperatingReportVO.Row month(OperatingReportService report, LocalDate d) {
        return report.year(d.getYear(), null).getRows().stream()
                .filter(r -> r.getMonth() != null && r.getMonth() == d.getMonthValue())
                .findFirst().orElse(null);
    }

    /** PI 转成的有效订单合计（部分取消、改费用后与 PI 合计不同） */
    private static Map<Long, BigDecimal> activeOrderTotals(SalesOrderMapper orders, List<ProformaInvoiceDO> pis) {
        if (pis.isEmpty()) {
            return Map.of();
        }
        return orders.selectList(new LambdaQueryWrapper<SalesOrderDO>()
                        .select(SalesOrderDO::getPiId, SalesOrderDO::getTotalAmount)
                        .in(SalesOrderDO::getPiId, pis.stream().map(ProformaInvoiceDO::getId).toList())
                        .eq(SalesOrderDO::getStatus, SalesConstants.SO_ACTIVE)
                        .isNull(SalesOrderDO::getDeletedAt))
                .stream().collect(Collectors.toMap(SalesOrderDO::getPiId, o -> W.num(o.getTotalAmount()), (a, b) -> a));
    }

    private static BigDecimal revenue(OperatingReportVO.Row r) {
        return r == null ? BigDecimal.ZERO : W.num(r.getRevenue());
    }

    private static BigDecimal profit(OperatingReportVO.Row r) {
        return r == null ? BigDecimal.ZERO : W.num(r.getProfit());
    }

    /** 卡片副标题里的金额：两位小数（HALF_UP）并加千分位，与前端 formatAmount 一致 */
    static String money(BigDecimal v) {
        return String.format(Locale.ROOT, "%,.2f", v.setScale(2, RoundingMode.HALF_UP));
    }
}
