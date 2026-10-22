package com.zhul.erp.modules.workbench.widget;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.framework.security.DataScopeResolver;
import com.zhul.erp.framework.security.PermissionChecker;
import com.zhul.erp.modules.crm.dto.OpportunityStatsVO;
import com.zhul.erp.modules.crm.service.OpportunityService;
import com.zhul.erp.modules.inquiry.constants.InquiryConstants;
import com.zhul.erp.modules.inquiry.customerinquiry.entity.CustomerInquiryDO;
import com.zhul.erp.modules.inquiry.customerinquiry.repository.CustomerInquiryMapper;
import com.zhul.erp.modules.masterdata.repository.CustomerMapper;
import com.zhul.erp.modules.sales.constants.SalesConstants;
import com.zhul.erp.modules.sales.dto.OverduePiVO;
import com.zhul.erp.modules.sales.dto.SalesStatsQuery;
import com.zhul.erp.modules.sales.dto.SalesStatsVO;
import com.zhul.erp.modules.sales.entity.ProformaInvoiceDO;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.repository.ProformaInvoiceMapper;
import com.zhul.erp.modules.sales.repository.SalesOrderMapper;
import com.zhul.erp.modules.sales.service.PiService;
import com.zhul.erp.modules.sales.service.SalesOrderService;
import com.zhul.erp.modules.sales.service.SalesStatsService;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.system.repository.UserBasicMapper;
import com.zhul.erp.modules.workbench.dto.WidgetData;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 业务员看板与业务负责人看板（spec「业务员看板」「业务负责人看板」）。
 * 数据都按当前用户的数据范围：业务员看自己，负责人看团队；口径同销售统计、询盘单、PI、销售订单页。
 */
@Configuration
public class SalesWidgets {

    static final String INQUIRIES = "/inquiry/customer-inquiries";
    static final String PIS = "/sales/pi";
    static final String ORDERS = "/sales/orders";
    static final String STATS = "/sales/stats";
    static final String OPPORTUNITIES = "/crm/opportunities";
    static final String OPP_STATS = "/crm/opportunity-stats";

    /** 采购已回价、超过这么久还没报价的询盘算待跟进 */
    static final long FOLLOW_UP_HOURS = 24;

    // ---------------------------------------------------------------- 业务员

    @Bean
    public WorkbenchWidget myTodoWidget(PermissionChecker perm, DataScopeResolver scope, CustomerInquiryMapper inquiries,
                                        ProformaInvoiceMapper pis, SalesOrderMapper orders) {
        return W.widget("my-todo", List.of(INQUIRIES, PIS, ORDERS), () -> {
            List<WidgetData.Todo> todos = new ArrayList<>();
            if (perm.canAccessMenu(INQUIRIES)) {
                todos.add(W.todo("inbox", "blue", "待确认的询盘", "AI 解析完成，等你核对型号",
                        inquiries.selectCount(inquiry(scope).eq(CustomerInquiryDO::getStatus, InquiryConstants.STATUS_PENDING_CONFIRM)),
                        true, INQUIRIES));
                todos.add(W.todo("file-text", "green", "可以报价", "采购已回价，还没做报价单",
                        inquiries.selectCount(inquiry(scope).eq(CustomerInquiryDO::getStatus, InquiryConstants.STATUS_READY_TO_QUOTE)),
                        true, INQUIRIES));
            }
            if (perm.canAccessMenu(PIS)) {
                todos.add(W.todo("wallet", "orange", "PI 待收款", "已发给客户，还没付清",
                        pis.selectCount(unpaidPis(scope)), false, PIS));
            }
            if (perm.canAccessMenu(ORDERS)) {
                LambdaQueryWrapper<SalesOrderDO> w = new LambdaQueryWrapper<SalesOrderDO>()
                        .eq(SalesOrderDO::getTenantId, PiStore.tenantId())
                        .eq(SalesOrderDO::getStatus, SalesConstants.SO_ACTIVE)
                        .eq(SalesOrderDO::getProgressCode, SalesConstants.PROGRESS_RECEIVED)
                        .isNull(SalesOrderDO::getDeletedAt);
                scope.current().apply(w, SalesOrderDO::getOwnerId);
                todos.add(W.todo("ship", "violet", "订单待出运", "货已到齐，等安排交货代", orders.selectCount(w), false, ORDERS));
            }
            return W.of(W.todos(todos));
        });
    }

    @Bean
    public WorkbenchWidget myPerformanceWidget(SalesStatsService stats, SalesOrderService orders) {
        return W.widget("my-performance", List.of(ORDERS, STATS), () -> performance(stats, orders, false));
    }

    @Bean
    public WorkbenchWidget quoteProgressWidget(DataScopeResolver scope, CustomerInquiryMapper inquiries) {
        return W.widget("quote-progress", List.of(INQUIRIES), () -> {
            List<CustomerInquiryDO> sourcing = inquiries.selectList(inquiry(scope)
                    .select(CustomerInquiryDO::getId, CustomerInquiryDO::getTotalItemCount, CustomerInquiryDO::getPricedItemCount,
                            CustomerInquiryDO::getQuoteDeadline)
                    .eq(CustomerInquiryDO::getStatus, InquiryConstants.STATUS_SOURCING));
            long waiting = sourcing.stream().mapToLong(i -> Math.max(0,
                    W.num(i.getTotalItemCount()).longValue() - W.num(i.getPricedItemCount()).longValue())).sum();
            LocalDate today = LocalDate.now();
            long late = sourcing.stream().filter(i -> i.getQuoteDeadline() != null && i.getQuoteDeadline().isBefore(today)).count();
            return W.of(W.metrics(
                    W.link(W.metric("等采购回价的型号", waiting, "count", "来自 " + sourcing.size() + " 张询盘"), INQUIRIES),
                    W.tone(W.metric("已过报价截止", late, "count", late > 0 ? "截止日期已过，还在询价" : "都还没到截止日期"),
                            late > 0 ? "red" : null)));
        });
    }

    @Bean
    public WorkbenchWidget myOpportunitiesWidget(OpportunityService opportunities) {
        return W.widget("my-opportunities", List.of(OPPORTUNITIES, OPP_STATS), () -> {
            OpportunityStatsVO.Row s = opportunities.stats(W.monthStart(), LocalDate.now(), "channel").getSummary();
            long total = s == null ? 0 : s.getTotal();
            long valid = s == null ? 0 : s.getValid();
            long won = s == null ? 0 : s.getWon();
            BigDecimal r = W.rate(valid, total);
            return W.of(W.metrics(
                    W.link(W.metric("本月新增商机", total, "count", null), OPPORTUNITIES),
                    W.metric("有效商机", valid, "count", r == null ? "有效率 —" : "有效率 " + r.toPlainString() + "%"),
                    W.metric("赢单", won, "count", null)));
        });
    }

    // ---------------------------------------------------------------- 业务负责人

    @Bean
    public WorkbenchWidget teamPerformanceWidget(SalesStatsService stats, SalesOrderService orders) {
        return W.widget("team-performance", List.of(STATS, ORDERS), () -> performance(stats, orders, true));
    }

    @Bean
    public WorkbenchWidget followUpsWidget(PermissionChecker perm, DataScopeResolver scope, CustomerInquiryMapper inquiries,
                                           CustomerMapper customers, UserBasicMapper users, PiService piService) {
        return W.widget("follow-ups", List.of(INQUIRIES, PIS), () -> {
            List<WidgetData.Todo> todos = new ArrayList<>();
            WidgetData.Block table = null;
            if (perm.canAccessMenu(INQUIRIES)) {
                LocalDateTime before = LocalDateTime.now().minusHours(FOLLOW_UP_HOURS);
                List<CustomerInquiryDO> waiting = inquiries.selectList(inquiry(scope)
                        .eq(CustomerInquiryDO::getStatus, InquiryConstants.STATUS_READY_TO_QUOTE)
                        .le(CustomerInquiryDO::getUpdateTime, before)
                        .orderByAsc(CustomerInquiryDO::getUpdateTime));
                todos.add(W.todo("hourglass", "orange", "回价后超过 24 小时未报价",
                        waiting.isEmpty() ? "采购回价的询盘都已及时报价" : "最久的 " + waiting.get(0).getInquiryCode() + " 已等 "
                                + hours(waiting.get(0).getUpdateTime()) + " 小时", waiting.size(), true, INQUIRIES));
                if (!waiting.isEmpty()) {
                    List<CustomerInquiryDO> top = waiting.subList(0, Math.min(5, waiting.size()));
                    Map<Long, String> custNames = new HashMap<>();
                    List<Long> custIds = top.stream().map(CustomerInquiryDO::getCustomerId).filter(Objects::nonNull).distinct().toList();
                    if (!custIds.isEmpty()) {
                        customers.selectBatchIds(custIds).forEach(c -> custNames.put(c.getId(), c.getName()));
                    }
                    Map<Long, String> ownerNames = userNames(users, top.stream().map(CustomerInquiryDO::getOwnerId).toList());
                    WidgetData.Table t = new WidgetData.Table();
                    t.getColumns().add(WidgetData.Column.of("询盘", "l", null));
                    t.getColumns().add(WidgetData.Column.of("业务员", "l", null));
                    t.getColumns().add(WidgetData.Column.of("已等待", "r", null));
                    t.setLinks(new ArrayList<>());
                    for (CustomerInquiryDO i : top) {
                        t.getRows().add(List.of(i.getInquiryCode() + (custNames.get(i.getCustomerId()) == null ? ""
                                        : " · " + custNames.get(i.getCustomerId())),
                                ownerNames.getOrDefault(i.getOwnerId(), "—"), hours(i.getUpdateTime()) + " 小时"));
                        t.getLinks().add(INQUIRIES + "/" + i.getId());
                    }
                    table = W.table("等待最久的", t);
                }
            }
            if (perm.canAccessMenu(PIS)) {
                OverduePiVO overdue = piService.overdue();
                todos.add(W.todo("file-clock", "red", "过期未收款 PI", "已过有效期仍未收到款",
                        overdue.getCount() == null ? 0 : overdue.getCount(), true, PIS + "?expired=1"));
            }
            return W.of(W.todos(todos), table);
        });
    }

    @Bean
    public WorkbenchWidget salesCompareWidget(SalesStatsService stats) {
        return W.widget("sales-compare", List.of(STATS), () -> {
            SalesStatsVO vo = stats.stats(query(W.monthStart(), LocalDate.now(), "OWNER"));
            WidgetData.Table t = new WidgetData.Table();
            t.getColumns().add(WidgetData.Column.of("业务员", "l", null));
            t.getColumns().add(WidgetData.Column.of("询盘", "r", "count"));
            t.getColumns().add(WidgetData.Column.of("报价", "r", "count"));
            t.getColumns().add(WidgetData.Column.of("成交额", "r", "money"));
            t.getColumns().add(WidgetData.Column.of("询盘→成交", "r", "percent"));
            t.setEmpty("本月还没有业务数据");
            vo.getRows().stream()
                    .sorted(Comparator.comparing((SalesStatsVO.Row r) -> W.num(r.getRevenue() == null ? null : r.getRevenue().getAll()))
                            .reversed())
                    .forEach(r -> t.getRows().add(List.of(r.getLabel(), r.getInquiries().getAll(), r.getQuotations().getAll(),
                            W.num(r.getRevenue() == null ? null : r.getRevenue().getAll()),
                            r.getOrderRate() == null ? "—" : r.getOrderRate())));
            return W.of(W.table(null, t));
        });
    }

    @Bean
    public WorkbenchWidget quoteConversionWidget(SalesStatsService stats) {
        return W.widget("quote-conversion", List.of(STATS), () -> {
            SalesStatsVO vo = stats.stats(query(W.monthStart().minusMonths(5), LocalDate.now(), "MONTH"));
            WidgetData.Bars bars = new WidgetData.Bars();
            bars.setFormat("percent");
            String last = null;
            for (SalesStatsVO.Row r : vo.getRows()) {
                int quotes = r.getQuotations().getAll();
                int intents = r.getIntents().getAll();
                BigDecimal rate = W.rate(intents, quotes);
                bars.getLabels().add(r.getLabel());
                bars.getValues().add(rate == null ? BigDecimal.ZERO : rate);
                last = r.getLabel() + " 报价 " + quotes + "、转 PI " + intents + (rate == null ? "" : "，比例 " + rate.toPlainString() + "%");
            }
            return W.of(W.bars(bars, last == null ? "近 6 个月还没有发送的报价" : last));
        });
    }

    // ---------------------------------------------------------------- 工具

    /** 本月与上月：成交额、毛利（率）、实收、订单数；负责人另看询盘与报价 */
    private static WidgetData performance(SalesStatsService stats, SalesOrderService orders, boolean team) {
        SalesStatsVO.Row cur = stats.stats(query(W.monthStart(), LocalDate.now(), "MONTH")).getTotal();
        SalesStatsVO.Row prev = stats.stats(query(W.lastMonthStart(), W.lastMonthEnd(), "MONTH")).getTotal();
        BigDecimal received = orders.receivedNetCny(W.monthStart(), LocalDate.now());
        BigDecimal receivedPrev = orders.receivedNetCny(W.lastMonthStart(), W.lastMonthEnd());
        List<WidgetData.Metric> items = new ArrayList<>();
        if (team) {
            items.add(W.compare("新增询盘", cur.getInquiries().getAll(), prev.getInquiries().getAll(), "count", true));
            items.add(W.compare("报价单", cur.getQuotations().getAll(), prev.getQuotations().getAll(), "count", true));
        }
        items.add(W.compare("成交额", money(cur.getRevenue()), money(prev.getRevenue()), "money", true));
        if (team) {
            items.add(W.compare("毛利率", cur.getProfitRate(), prev.getProfitRate(), "percent", true));
        } else {
            items.add(W.compare("毛利", money(cur.getProfit()), money(prev.getProfit()), "money", true));
        }
        items.add(W.compare("实收回款", received, receivedPrev, "money", true));
        if (!team) {
            items.add(W.compare("成交订单", cur.getOrders().getAll(), prev.getOrders().getAll(), "count", true));
        }
        return W.of(W.metrics(items));
    }

    private static BigDecimal money(SalesStatsVO.Money m) {
        return m == null ? BigDecimal.ZERO : W.num(m.getAll());
    }

    static SalesStatsQuery query(LocalDate from, LocalDate to, String view) {
        SalesStatsQuery q = new SalesStatsQuery();
        q.setDateFrom(from);
        q.setDateTo(to);
        q.setView(view);
        return q;
    }

    private static LambdaQueryWrapper<CustomerInquiryDO> inquiry(DataScopeResolver scope) {
        LambdaQueryWrapper<CustomerInquiryDO> w = new LambdaQueryWrapper<CustomerInquiryDO>()
                .eq(CustomerInquiryDO::getTenantId, PiStore.tenantId())
                .isNull(CustomerInquiryDO::getDeletedAt);
        return scope.current().apply(w, CustomerInquiryDO::getOwnerId);
    }

    /** 已发出、还没付清（含只有水单、部分到账）的 PI */
    static LambdaQueryWrapper<ProformaInvoiceDO> unpaidPis(DataScopeResolver scope) {
        LambdaQueryWrapper<ProformaInvoiceDO> w = new LambdaQueryWrapper<ProformaInvoiceDO>()
                .eq(ProformaInvoiceDO::getTenantId, PiStore.tenantId())
                .in(ProformaInvoiceDO::getStatus, SalesConstants.PI_SENT, SalesConstants.PI_CONVERTED)
                .ne(ProformaInvoiceDO::getReceiptStatus, SalesConstants.RECEIPT_PAID)
                .isNull(ProformaInvoiceDO::getDeletedAt);
        return scope.current().apply(w, ProformaInvoiceDO::getOwnerId);
    }

    static Map<Long, String> userNames(UserBasicMapper users, List<Long> ids) {
        List<Long> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        Map<Long, String> out = new HashMap<>();
        if (!distinct.isEmpty()) {
            users.selectBatchIds(distinct).forEach(u -> out.put(u.getId() == null ? null : u.getId().longValue(), u.getName()));
        }
        return out;
    }

    private static long hours(LocalDateTime since) {
        return since == null ? 0 : Math.max(0, Duration.between(since, LocalDateTime.now()).toHours());
    }
}
