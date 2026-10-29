package com.zhul.erp.modules.system.migration.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * 与旧报表核对（spec「与旧报表核对」）：按统计口径从数据库汇总迁移后的数据，与迁移包里的旧报表数字逐月、逐业务员、逐客户比较。
 * 金额按「订单数 × ¥0.01」容差（至少 ¥0.01），数量必须相等；已知差异单独标注。迁移订单取成本核算快照，退款按退款日期。
 * 结果 JSON：summary（共几项、一致、已知差异、不一致）、byMetric（按报表与指标汇总）、rows（明细）。
 */
@Component
@RequiredArgsConstructor
public class MigrationVerifier {

    private static final String CONSISTENT = "一致";
    private static final String KNOWN = "已知差异";
    private static final String INCONSISTENT = "不一致";
    private static final Pattern NEW_OLD_INQUIRY = Pattern.compile("询盘数（(新|老)客户）");

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    /** 一个范围的汇总值（月、业务员或客户） */
    private static final class Agg {
        final Map<String, String> money = new HashMap<>();
        final Map<String, Long> count = new HashMap<>();

        long n(String k) {
            return count.getOrDefault(k, 0L);
        }

        String m(String k) {
            return money.getOrDefault(k, "0");
        }

        void add(String k, String v) {
            money.put(k, new BigDecimal(m(k)).add(new BigDecimal(v == null ? "0" : v)).toPlainString());
        }
    }

    public String verify(int tenantId, Map<String, JsonNode> bundle) {
        JsonNode reports = bundle.get("reports");
        Map<String, Agg> months = new HashMap<>();
        Map<String, Agg> staff = new HashMap<>();
        Map<String, Agg> customers = new HashMap<>();
        collect(tenantId, months, staff, customers);
        Map<String, Long> customerIds = new HashMap<>();
        jdbc.query("SELECT source_code, target_id FROM migration_id_map WHERE tenant_id = ? AND status = 1 AND source_table = 'customers' AND target_table = 'customer'",
                rs -> {
                    customerIds.put(rs.getString(1), rs.getLong(2));
                }, tenantId);

        List<Map<String, Object>> rows = new ArrayList<>();
        Function<String, Agg> month = k -> months.getOrDefault(k, new Agg());
        for (JsonNode x : reports.path("operating")) {
            String ym = str(x, "month");
            Agg d = month.apply(ym);
            long orders = d.n("orders");
            money(rows, "公司经营报表", ym, "销售收入", str(x, "revenue"), d.m("revenue"), orders, Map.of());
            money(rows, "公司经营报表", ym, "销售成本", str(x, "cost"), d.m("cost"), orders, Map.of());
            money(rows, "公司经营报表", ym, "退款", str(x, "refund"), d.m("refund"), 0, Map.of());
            money(rows, "公司经营报表", ym, "其他收入", str(x, "otherIncome"), d.m("otherIncome"), 0, Map.of());
            money(rows, "公司经营报表", ym, "运营成本合计", str(x, "opex"), d.m("opex"), 0, Map.of());
        }
        for (JsonNode x : reports.path("companySales")) {
            String ym = str(x, "month");
            Agg d = month.apply(ym);
            long orders = d.n("orders");
            count(rows, "公司销售报表", ym, "商机数", x.path("opportunities").asLong(), d.n("opportunities"), Map.of());
            count(rows, "公司销售报表", ym, "询盘数", x.path("inquiries").asLong(), d.n("inquiryNew") + d.n("inquiryOld"), Map.of());
            count(rows, "公司销售报表", ym, "询盘数（新客户）", x.path("inquiriesNew").asLong(), d.n("inquiryNew"), Map.of());
            count(rows, "公司销售报表", ym, "询盘数（老客户）", x.path("inquiriesOld").asLong(), d.n("inquiryOld"), Map.of());
            count(rows, "公司销售报表", ym, "销售订单数", x.path("orders").asLong(), d.n("ordersNew") + d.n("ordersOld"),
                    Map.of("noSnapshot", d.n("noSnapshot")));
            count(rows, "公司销售报表", ym, "销售订单数（新客户）", x.path("ordersNew").asLong(), d.n("ordersNew"),
                    Map.of("noSnapshot", d.n("noSnapshotNew")));
            count(rows, "公司销售报表", ym, "销售订单数（老客户）", x.path("ordersOld").asLong(), d.n("ordersOld"),
                    Map.of("noSnapshot", d.n("noSnapshotOld")));
            count(rows, "公司销售报表", ym, "成交客户数", x.path("dealCustomers").asLong(), d.n("dealCustomers"),
                    Map.of("placeholders", d.n("placeholderCustomers")));
            money(rows, "公司销售报表", ym, "销售收入", str(x, "revenue"), d.m("salesRevenue"), orders, Map.of());
            money(rows, "公司销售报表", ym, "销售毛利", str(x, "profit"), d.m("salesProfit"), orders, Map.of());
        }
        for (JsonNode x : reports.path("staffSales")) {
            String ym = str(x, "month");
            Agg s = staff.getOrDefault(ym + "|" + str(x, "staff"), new Agg());
            String scope = ym + "|" + str(x, "staff");
            count(rows, "业务销售报表", scope, "询盘数", x.path("inquiries").asLong(), s.n("inquiries"), Map.of());
            count(rows, "业务销售报表", scope, "销售订单数", x.path("orders").asLong(), s.n("orders"), Map.of());
            money(rows, "业务销售报表", scope, "销售收入", str(x, "revenue"), s.m("revenue"), s.n("orders"), Map.of());
            money(rows, "业务销售报表", scope, "销售毛利", str(x, "profit"), s.m("profit"), s.n("orders"), Map.of());
        }
        for (JsonNode x : reports.path("customerSales")) {
            String ym = str(x, "month");
            Long cid = customerIds.get(str(x, "customerKey"));
            Agg c = customers.getOrDefault(ym + "|" + cid, new Agg());
            String scope = ym + "|" + str(x, "code") + " " + str(x, "contact");
            Map<String, Object> ctx = Map.of("blankCustomer", str(x, "code").isEmpty(), "noMonth", ym == null);
            count(rows, "客户销售报表", scope, "订单数", x.path("orders").asLong(), c.n("orders"), ctx);
            money(rows, "客户销售报表", scope, "销售收入", str(x, "revenue"), c.m("revenue"), c.n("orders"), ctx);
            money(rows, "客户销售报表", scope, "销售毛利", str(x, "profit"), c.m("profit"), c.n("orders"), ctx);
        }
        return toJson(rows);
    }

    /** 新系统的统计口径，与 scripts/history-migration/verify.js 原来的 SQL 一致 */
    private void collect(int t, Map<String, Agg> months, Map<String, Agg> staff, Map<String, Agg> customers) {
        String so = "sales_order so LEFT JOIN sales_order_cost_snapshot s ON s.so_id = so.id AND s.deleted_at IS NULL"
                + " LEFT JOIN (SELECT so_id, SUM(amount_cny) refund FROM fin_customer_refund WHERE tenant_id = ? AND status = 1 AND deleted_at IS NULL GROUP BY so_id) f ON f.so_id = so.id"
                + " %s WHERE so.tenant_id = ? AND so.deleted_at IS NULL AND so.status = 1";
        String plain = so.formatted("");
        String net = "SUM(IFNULL(s.revenue, 0) - IFNULL(f.refund, 0))";
        String profit = "SUM(IFNULL(s.revenue, 0) - IFNULL(f.refund, 0) - IFNULL(s.cost, 0))";
        Function<String, Agg> at = k -> months.computeIfAbsent(k, x -> new Agg());
        // 经营统计：按销售日期，收入与成本不扣退款
        jdbc.query("SELECT DATE_FORMAT(so.sales_date, '%Y-%m'), SUM(IFNULL(s.revenue, 0)), SUM(IFNULL(s.cost, 0)), COUNT(*) FROM " + plain + " GROUP BY 1", rs -> {
            Agg a = at.apply(rs.getString(1));
            a.money.put("revenue", rs.getString(2));
            a.money.put("cost", rs.getString(3));
            a.count.put("orders", rs.getLong(4));
        }, t, t);
        jdbc.query("SELECT DATE_FORMAT(refund_date, '%Y-%m'), SUM(amount_cny) FROM fin_customer_refund WHERE tenant_id = ? AND status = 1 AND deleted_at IS NULL GROUP BY 1",
                rs -> {
                    at.apply(rs.getString(1)).money.put("refund", rs.getString(2));
                }, t);
        jdbc.query("SELECT DATE_FORMAT(income_date, '%Y-%m'), SUM(amount) FROM fin_other_income WHERE tenant_id = ? AND status = 1 AND deleted_at IS NULL GROUP BY 1",
                rs -> {
                    at.apply(rs.getString(1)).money.put("otherIncome", rs.getString(2));
                }, t);
        jdbc.query("SELECT DATE_FORMAT(occur_date, '%Y-%m'), SUM(amount_cny) FROM fin_expense WHERE tenant_id = ? AND status = 1 AND deleted_at IS NULL AND category <> 'ORDER' GROUP BY 1",
                rs -> {
                    at.apply(rs.getString(1)).money.put("opex", rs.getString(2));
                }, t);
        // 销售统计
        jdbc.query("SELECT DATE_FORMAT(inquiry_date, '%Y-%m'), customer_type, COUNT(*) FROM customer_inquiry WHERE tenant_id = ? AND deleted_at IS NULL AND status <> 10 GROUP BY 1, 2",
                rs -> {
                    at.apply(rs.getString(1)).count.put(rs.getInt(2) == 2 ? "inquiryOld" : "inquiryNew", rs.getLong(3));
                }, t);
        jdbc.query("SELECT DATE_FORMAT(so.sales_date, '%Y-%m'), so.customer_type, COUNT(*), " + net + ", " + profit + " FROM " + plain + " GROUP BY 1, 2", rs -> {
            Agg a = at.apply(rs.getString(1));
            a.count.put(rs.getInt(2) == 2 ? "ordersOld" : "ordersNew", rs.getLong(3));
            a.add("salesRevenue", rs.getString(4));
            a.add("salesProfit", rs.getString(5));
        }, t, t);
        jdbc.query("SELECT DATE_FORMAT(so.sales_date, '%Y-%m'), COUNT(DISTINCT so.customer_id) FROM " + plain + " GROUP BY 1", rs -> {
            at.apply(rs.getString(1)).count.put("dealCustomers", rs.getLong(2));
        }, t, t);
        // 没有核算单（快照）的订单：旧公司销售报表按核算单计数，不计入
        jdbc.query("SELECT DATE_FORMAT(so.sales_date, '%Y-%m'), so.customer_type, COUNT(*) FROM " + plain + " AND s.id IS NULL GROUP BY 1, 2", rs -> {
            Agg a = at.apply(rs.getString(1));
            a.count.merge("noSnapshot", rs.getLong(3), Long::sum);
            a.count.put(rs.getInt(2) == 2 ? "noSnapshotOld" : "noSnapshotNew", rs.getLong(3));
        }, t, t);
        // 没填客户的订单挂在占位客户「未登记客户（历史迁移）-业务员」上，旧报表不把它们算成成交客户
        jdbc.query("SELECT DATE_FORMAT(so.sales_date, '%Y-%m'), COUNT(DISTINCT so.customer_id) FROM "
                + so.formatted("JOIN customer c ON c.id = so.customer_id") + " AND c.name LIKE '未登记客户（历史迁移）%' GROUP BY 1", rs -> {
            at.apply(rs.getString(1)).count.put("placeholderCustomers", rs.getLong(2));
        }, t, t);
        jdbc.query("SELECT DATE_FORMAT(first_contact_date, '%Y-%m'), COUNT(*) FROM opportunity WHERE tenant_id = ? AND deleted_at IS NULL GROUP BY 1", rs -> {
            at.apply(rs.getString(1)).count.put("opportunities", rs.getLong(2));
        }, t);
        // 业务员、客户
        jdbc.query("SELECT DATE_FORMAT(so.sales_date, '%Y-%m'), u.name, COUNT(*), " + net + ", " + profit + " FROM "
                + so.formatted("LEFT JOIN user_basic u ON u.id = so.owner_id") + " GROUP BY 1, 2", rs -> {
            Agg a = staff.computeIfAbsent(rs.getString(1) + "|" + rs.getString(2), x -> new Agg());
            a.count.put("orders", rs.getLong(3));
            a.money.put("revenue", rs.getString(4));
            a.money.put("profit", rs.getString(5));
        }, t, t);
        jdbc.query("SELECT DATE_FORMAT(q.inquiry_date, '%Y-%m'), u.name, COUNT(*) FROM customer_inquiry q LEFT JOIN user_basic u ON u.id = q.owner_id"
                + " WHERE q.tenant_id = ? AND q.deleted_at IS NULL AND q.status <> 10 GROUP BY 1, 2", rs -> {
            staff.computeIfAbsent(rs.getString(1) + "|" + rs.getString(2), x -> new Agg()).count.put("inquiries", rs.getLong(3));
        }, t);
        jdbc.query("SELECT DATE_FORMAT(so.sales_date, '%Y-%m'), so.customer_id, COUNT(*), " + net + ", " + profit + " FROM " + plain + " GROUP BY 1, 2", rs -> {
            Agg a = customers.computeIfAbsent(rs.getString(1) + "|" + rs.getLong(2), x -> new Agg());
            a.count.put("orders", rs.getLong(3));
            a.money.put("revenue", rs.getString(4));
            a.money.put("profit", rs.getString(5));
        }, t, t);
    }

    private static void money(List<Map<String, Object>> rows, String report, String scope, String metric, String oldV, String newV,
                              long orders, Map<String, Object> ctx) {
        long o = cents(oldV);
        long n = cents(newV);
        long tol = Math.max(1, orders);
        boolean same = Math.abs(o - n) <= tol;
        String known = same ? "" : knownDifference(metric, month(scope), o, n, ctx);
        rows.add(row(report, scope, metric, yuan(o), yuan(n), yuan(n - o), yuan(tol), same ? CONSISTENT : known.isEmpty() ? INCONSISTENT : KNOWN, known));
    }

    private static void count(List<Map<String, Object>> rows, String report, String scope, String metric, long o, long n, Map<String, Object> ctx) {
        String known = o == n ? "" : knownDifference(metric, month(scope), o, n, ctx);
        rows.add(row(report, scope, metric, o, n, n - o, 0, o == n ? CONSISTENT : known.isEmpty() ? INCONSISTENT : KNOWN, known));
    }

    private static Map<String, Object> row(String report, String scope, String metric, Object oldV, Object newV, Object diff, Object tol,
                                           String verdict, String note) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("report", report);
        r.put("scope", scope == null ? "（没有月份）" : scope);
        r.put("metric", metric);
        r.put("old", oldV);
        r.put("new", newV);
        r.put("diff", diff);
        r.put("tolerance", tol);
        r.put("verdict", verdict);
        r.put("note", note);
        return r;
    }

    /** 已知差异：旧报表自身口径不一致、或已确认不迁移的数据（规则与 verify.js 原来的一致） */
    static String knownDifference(String metric, String month, long oldV, long newV, Map<String, Object> ctx) {
        long noSnapshot = ((Number) ctx.getOrDefault("noSnapshot", 0L)).longValue();
        long placeholders = ((Number) ctx.getOrDefault("placeholders", 0L)).longValue();
        Set<String> aprMay = Set.of("2026-04", "2026-05");
        boolean costOrProfit = metric.equals("销售成本") || metric.equals("销售毛利");
        if (costOrProfit && aprMay.contains(month)) {
            return "旧报表把退款核算单的成本计入了当月销售成本，其他月份没有计入";
        }
        if ("2026-10".equals(month) && costOrProfit) {
            return "两份文件导出时间不同，中间改过成本（冻结日同时导出可避免）";
        }
        if ("2026-10".equals(month) && metric.equals("询盘数") && oldV - newV == 1) {
            return "两份文件导出时间不同：导出报表时多一张询盘，导出询盘表时已不在（新系统与询盘表一致；冻结日同时导出可避免）";
        }
        if (metric.equals("成交客户数") && "2026-10".equals(month) && oldV == 0) {
            return "旧报表当月成交客户数尚未计算";
        }
        if (metric.equals("成交客户数")) {
            return "旧报表的成交客户数取自《客户销售报表》当月的行数（月结时生成），新系统按订单的客户去重"
                    + (placeholders > 0 ? "，并含 " + placeholders + " 个「未登记客户（历史迁移）」占位客户" : "");
        }
        if (metric.startsWith("销售订单数") && aprMay.contains(month) && newV - noSnapshot + 1 == oldV) {
            return "旧报表把退款核算单也算成一张订单";
        }
        if (metric.startsWith("销售订单数") && noSnapshot > 0 && newV - noSnapshot == oldV) {
            return "旧公司销售报表按核算单计数，" + noSnapshot + " 张没有核算单的订单不计入";
        }
        if (Boolean.TRUE.equals(ctx.get("blankCustomer")) && metric.equals("订单数") && oldV == 0) {
            return "旧客户销售报表里没填客户的行，订单数记为 0";
        }
        if (Boolean.TRUE.equals(ctx.get("noMonth"))) {
            return "旧报表这一行没有销售日期（退款核算单）";
        }
        if (metric.equals("退款")) {
            return "退款单没有关联订单，按决定不迁移";
        }
        if (metric.startsWith("商机数")) {
            return "商机只迁移有跟进阶段或有询盘的";
        }
        if (NEW_OLD_INQUIRY.matcher(metric).find()) {
            return "旧系统新老客户的拆分规则推不出来；新系统按询盘日期前是否已有订单判断，每月询盘总数一致";
        }
        return "";
    }

    private String toJson(List<Map<String, Object>> rows) {
        Map<String, Integer> summary = new LinkedHashMap<>();
        summary.put("total", rows.size());
        summary.put("consistent", 0);
        summary.put("known", 0);
        summary.put("inconsistent", 0);
        Map<String, Map<String, Object>> byMetric = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            String v = (String) r.get("verdict");
            String key = v.equals(CONSISTENT) ? "consistent" : v.equals(KNOWN) ? "known" : "inconsistent";
            summary.merge(key, 1, Integer::sum);
            Map<String, Object> m = byMetric.computeIfAbsent(r.get("report") + "|" + r.get("metric"), k -> {
                Map<String, Object> x = new LinkedHashMap<>();
                x.put("report", r.get("report"));
                x.put("metric", r.get("metric"));
                x.put("consistent", 0);
                x.put("known", 0);
                x.put("inconsistent", 0);
                return x;
            });
            m.merge(key, 1, (a, b) -> (Integer) a + (Integer) b);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("summary", summary);
        out.put("byMetric", byMetric.values());
        out.put("rows", rows);
        try {
            return objectMapper.writeValueAsString(out);
        } catch (IOException e) {
            throw new IllegalStateException("核对结果序列化失败", e);
        }
    }

    private static String str(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText().trim();
    }

    private static String month(String scope) {
        return scope == null ? null : scope.split("\\|")[0];
    }

    private static long cents(String amount) {
        return amount == null || amount.isBlank() ? 0
                : new BigDecimal(amount).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
    }

    private static String yuan(long cents) {
        return BigDecimal.valueOf(cents, 2).toPlainString();
    }
}
