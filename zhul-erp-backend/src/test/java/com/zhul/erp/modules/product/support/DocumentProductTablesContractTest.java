package com.zhul.erp.modules.product.support;

import com.zhul.erp.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 有 product_id 列的单据明细表都要在 {@link DocumentProductTables} 登记（合并商品、引用统计、历史回填按登记清单处理），
 * 不是单据的放进 NOT_DOCUMENT 并写明理由；商品模块自己的表（product 开头）不算单据。
 */
class DocumentProductTablesContractTest extends IntegrationTestBase {

    @Test
    void everyTableWithProductId_isRegisteredOrExcluded() {
        List<String> tables = jdbc.queryForList("select table_name from information_schema.columns "
                + "where table_schema = database() and column_name = 'product_id'", String.class);
        Set<String> registered = DocumentProductTables.ENTRIES.stream().map(DocumentProductTables.Entry::table)
                .collect(Collectors.toSet());
        List<String> missing = tables.stream()
                .filter(t -> !t.startsWith("product"))
                .filter(t -> !registered.contains(t) && !DocumentProductTables.NOT_DOCUMENT.containsKey(t))
                .sorted().toList();
        assertTrue(missing.isEmpty(), "这些表有 product_id 但没在 DocumentProductTables 登记：" + missing);
    }

    @Test
    void registeredTables_haveProductIdAndLinkColumns() {
        for (DocumentProductTables.Entry e : DocumentProductTables.ENTRIES) {
            assertTrue(hasColumn(e.table(), "product_id"), e.table() + " 没有 product_id 列");
            for (DocumentProductTables.Link l : e.chain()) {
                assertTrue(hasColumn(e.table(), l.column()), e.table() + " 没有来源列 " + l.column());
                assertTrue(hasColumn(l.parentTable(), "product_id"), l.parentTable() + " 没有 product_id 列");
            }
        }
    }

    private boolean hasColumn(String table, String column) {
        Integer n = jdbc.queryForObject("select count(*) from information_schema.columns where table_schema = database() "
                + "and table_name = ? and column_name = ?", Integer.class, table, column);
        return n != null && n > 0;
    }
}
