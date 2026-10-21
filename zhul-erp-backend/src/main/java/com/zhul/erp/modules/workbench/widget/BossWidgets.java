package com.zhul.erp.modules.workbench.widget;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.framework.security.DataScopeResolver;
import com.zhul.erp.framework.security.PermissionChecker;
import com.zhul.erp.modules.finance.dto.FxPendingVO;
import com.zhul.erp.modules.finance.dto.OperatingReportVO;
import com.zhul.erp.modules.finance.dto.PayableStatsVO;
import com.zhul.erp.modules.finance.service.FxSettlementService;
import com.zhul.erp.modules.finance.service.OperatingReportService;
import com.zhul.erp.modules.finance.service.PayableQueryService;
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
import com.zhul.erp.modules.warehouse.constants.WarehouseConstants;
import com.zhul.erp.modules.warehouse.dto.ShipmentPageQuery;
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
import java.util.stream.Collectors;

/**
 * 老板看板（spec「老板看板」）：经营概览、资金、销售漏斗、近 12 个月趋势、排行、风险提醒。
 * 口径同经营统计、销售统计、PI、应付单、结汇单页；数据范围按当前用户的角色（老板通常为全部）。
 */
@Configuration
public class BossWidgets {

    static final String OPERATING = "/finance/operating-report";
    static final String PROFIT = "/finance/profit-stats";
    static final String FX = "/finance/fx-settlements";

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
                                      PiService piService, PayableQueryService payables, FxSettlementService fx) {
        return W.widget("cash", List.of(SalesWidgets.PIS, PurchaseLeadWidgets.PAYABLES, FX), () -> {
            List<WidgetData.Metric> items = new ArrayList<>();
            if (perm.canAccessMenu(SalesWidgets.PIS)) {
                BigDecimal outstanding = BigDecimal.ZERO;
                for (ProformaInvoiceDO p : pis.selectList(SalesWidgets.unpaidPis(scope))) {
                    BigDecimal left = W.num(p.getTotalAmount()).subtract(W.num(p.getReceivedAmount()));
                    if (left.signum() > 0) {
                        outstanding = outstanding.add(left.multiply(W.num(p.getExchangeRate())));
                    }
                }
                OverduePiVO overdue = piService.overdue();
                String overdueText = overdue.getTotals() == null || overdue.getTotals().isEmpty() ? "没有过期未收款的 PI"
                        : "其中过期未收款 " + overdue.getTotals().stream()
                        .map(t -> t.getCurrencyCode() + " " + t.getAmount().setScale(2, RoundingMode.HALF_UP).toPlainString())
                        .collect(Collectors.joining("、"));
                items.add(W.link(W.tone(W.metric("应收未收", outstanding.setScale(2, RoundingMode.HALF_UP), "money", overdueText),
                        overdue.getTotals() == null || overdue.getTotals().isEmpty() ? null : "red"), SalesWidgets.PIS));
            }
            if (perm.canAccessMenu(PurchaseLeadWidgets.PAYABLES)) {
                PayableStatsVO s = payables.stats();
                items.add(W.link(W.tone(W.metric("本月到期应付", s.getMonthDue(), "money",
                        "已逾期 " + s.getOverdue().setScale(2, RoundingMode.HALF_UP).toPlainString() + " 元"),
                        s.getOverdueCount() > 0 ? "orange" : null), PurchaseLeadWidgets.PAYABLES));
            }
            WidgetData.Block tags = null;
            if (perm.canAccessMenu(FX)) {
                List<FxPendingVO> pending = fx.pending();
                tags = W.note(pending.isEmpty() ? "未结汇外币：没有待结汇的外币" : "未结汇外币");
                tags.setTags(pending.stream()
                        .map(p -> p.getCurrencyCode() + " " + p.getBalance().setScale(2, RoundingMode.HALF_UP).toPlainString())
                        .toList());
            }
            return W.of(items.isEmpty() ? null : W.metrics(items), tags);
        });
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
            OperatingReportVO lastYear = report.year(today.getYear() - 1);
            OperatingReportVO thisYear = report.year(today.getYear());
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
            OperatingReportVO.Row first = rows.get(0);
            OperatingReportVO.Row last = rows.get(11);
            String text = "毛利率 " + rateText(first) + " → " + rateText(last) + "（12 个月前 → 本月）";
            return W.of(W.bars(bars, text));
        });
    }

    @Bean
    public WorkbenchWidget riskAlertsWidget(PermissionChecker perm, PiService piService, ShipmentService shipments,
                                            DiscrepancyService discrepancies, DataScopeResolver scope, SalesOrderMapper orders) {
        return W.widget("risk-alerts", List.of(SalesWidgets.PIS, PurchaserWidgets.SHIPMENTS, SalesWidgets.ORDERS), () -> {
            List<WidgetData.Todo> todos = new ArrayList<>();
            if (perm.canAccessMenu(SalesWidgets.PIS)) {
                OverduePiVO o = piService.overdue();
                todos.add(W.todo("file-clock", "red", "过期未收款 PI", "已过有效期仍未收到款", o.getCount() == null ? 0 : o.getCount(),
                        true, SalesWidgets.PIS + "?expired=1"));
            }
            if (perm.canAccessMenu(PurchaserWidgets.SHIPMENTS)) {
                ShipmentPageQuery q = new ShipmentPageQuery();
                q.setPage(1);
                q.setPageSize(1000);
                q.setStatus(WarehouseConstants.SHIP_IN_TRANSIT);
                long late = shipments.page(q, true, false).getRecords().stream()
                        .filter(x -> Boolean.TRUE.equals(x.getArrivalOverdue())).count();
                todos.add(W.todo("truck", "orange", "超期未到货", "在途且已过预计到货日期", late, true, PurchaserWidgets.SHIPMENTS));
                todos.add(W.todo("warning", "orange", "未处理的到货差异", "少发、错发、不良待处理", discrepancies.countPending(), true,
                        PurchaserWidgets.SHIPMENTS + "?tab=diffs"));
            }
            if (perm.canAccessMenu(SalesWidgets.ORDERS)) {
                LambdaQueryWrapper<SalesOrderDO> w = new LambdaQueryWrapper<SalesOrderDO>()
                        .eq(SalesOrderDO::getTenantId, PiStore.tenantId())
                        .eq(SalesOrderDO::getStatus, SalesConstants.SO_ACTIVE)
                        .ge(SalesOrderDO::getSalesDate, W.monthStart())
                        .lt(SalesOrderDO::getNetProfitCny, BigDecimal.ZERO)
                        .isNull(SalesOrderDO::getDeletedAt);
                scope.current().apply(w, SalesOrderDO::getOwnerId);
                todos.add(W.todo("down", "red", "毛利为负的订单", "本月成交里亏本的订单", orders.selectCount(w), true,
                        SalesWidgets.ORDERS));
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
            (customers.getCustomers() == null ? List.<SalesStatsVO.CustomerRow>of() : customers.getCustomers()).stream()
                    .sorted(Comparator.comparing((SalesStatsVO.CustomerRow r) -> W.num(r.getRevenue())).reversed())
                    .limit(5)
                    .forEach(r -> t2.getRows().add(List.of(r.getCustomerName(), W.num(r.getRevenue()), r.getOrders())));
            return W.of(W.table("业务员 · 本月成交额", t1), W.table("客户 · 本月成交额", t2));
        });
    }

    // ---------------------------------------------------------------- 工具

    /** 经营统计里某个月的一行；没有（如未来月份）为空 */
    private static OperatingReportVO.Row month(OperatingReportService report, LocalDate d) {
        return report.year(d.getYear()).getRows().stream()
                .filter(r -> r.getMonth() != null && r.getMonth() == d.getMonthValue())
                .findFirst().orElse(null);
    }

    private static BigDecimal revenue(OperatingReportVO.Row r) {
        return r == null ? BigDecimal.ZERO : W.num(r.getRevenue());
    }

    private static BigDecimal profit(OperatingReportVO.Row r) {
        return r == null ? BigDecimal.ZERO : W.num(r.getProfit());
    }

    private static String rateText(OperatingReportVO.Row r) {
        return r == null || r.getProfitRate() == null ? "—" : r.getProfitRate().toPlainString() + "%";
    }
}
