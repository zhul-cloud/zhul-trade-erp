package com.zhul.erp.modules.masterdata.support;

import com.zhul.erp.modules.quotation.constants.QuotationConstants;
import com.zhul.erp.modules.sales.constants.SalesConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 单据里的品牌写法（openspec brand-unify）：汇总与统一都是有意的跨租户操作——品牌库是平台共用的，
 * 同一写法在各公司都应认成同一个品牌。只由平台账号调用（PendingBrandService 校验），只读写单据的品牌文字。
 * JdbcTemplate 不经过租户拦截器，所以这里不带租户条件。
 */
@Component
@RequiredArgsConstructor
public class BrandDocTexts {

    private final JdbcTemplate jdbc;

    /** 一个写法在各类单据里出现的行数 */
    public record Usage(String key, String name, int inquiry, int order, int purchase, LocalDateTime firstSeen) {
        public int total() {
            return inquiry + order + purchase;
        }
    }

    /** 按比较键（去首尾空格、转小写）汇总询盘型号、销售订单、采购订单里的品牌写法；空品牌不算 */
    public Map<String, Usage> usages() {
        Map<String, Usage> out = new HashMap<>();
        collect(out, "inquiry_item", 0);
        collect(out, "sales_order_item", 1);
        collect(out, "purchase_order_item", 2);
        return out;
    }

    private void collect(Map<String, Usage> out, String table, int slot) {
        jdbc.query("SELECT LOWER(TRIM(brand)) AS k, MIN(TRIM(brand)) AS n, COUNT(*) AS c, MIN(create_time) AS t FROM " + table
                + " WHERE deleted_at IS NULL AND TRIM(brand) <> '' GROUP BY LOWER(TRIM(brand))", rs -> {
            String k = rs.getString("k");
            int c = rs.getInt("c");
            Timestamp ts = rs.getTimestamp("t");
            LocalDateTime t = ts == null ? null : ts.toLocalDateTime();
            Usage u = out.get(k);
            String name = u == null ? rs.getString("n") : u.name();
            LocalDateTime first = u == null || (t != null && u.firstSeen() != null && t.isBefore(u.firstSeen())) ? t : u.firstSeen();
            out.put(k, new Usage(k, name, (u == null ? 0 : u.inquiry()) + (slot == 0 ? c : 0),
                    (u == null ? 0 : u.order()) + (slot == 1 ? c : 0), (u == null ? 0 : u.purchase()) + (slot == 2 ? c : 0), first));
        });
    }

    /**
     * 把这些写法统一成正式品牌名：询盘型号、销售订单、采购需求、采购订单全部改；
     * 报价单只改草稿与修改中的版本，PI 只改正在编辑的版本（已发给客户的不改）。返回改动的行数。
     */
    public int normalize(Collection<String> keys, String brandName) {
        if (keys.isEmpty()) {
            return 0;
        }
        NamedParameterJdbcTemplate named = new NamedParameterJdbcTemplate(jdbc);
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("keys", List.copyOf(keys)).addValue("name", brandName);
        String set = " SET t.brand = :name, t.update_time = NOW()";
        String where = " WHERE t.deleted_at IS NULL AND LOWER(TRIM(t.brand)) IN (:keys) AND BINARY t.brand <> BINARY :name";
        int n = 0;
        for (String table : List.of("inquiry_item", "sales_order_item", "purchase_requirement", "purchase_order_item")) {
            n += named.update("UPDATE " + table + " t" + set + where, p);
        }
        n += named.update("UPDATE quotation_item t JOIN quotation q ON q.id = t.quotation_id" + set + where
                + " AND q.deleted_at IS NULL AND ((q.status = " + QuotationConstants.STATUS_DRAFT + " AND t.is_current = 1)"
                + " OR (q.editing_version_no IS NOT NULL AND t.version_no = q.editing_version_no))", p);
        n += named.update("UPDATE proforma_invoice_item t JOIN proforma_invoice_version v ON v.id = t.version_id"
                + " JOIN proforma_invoice pi ON pi.id = t.pi_id" + set + where
                + " AND pi.deleted_at IS NULL AND v.version_no = pi.editing_version_no AND pi.status NOT IN ("
                + SalesConstants.PI_CONVERTED + ", " + SalesConstants.PI_VOID + ", " + SalesConstants.PI_CLOSED + ")", p);
        return n;
    }
}
