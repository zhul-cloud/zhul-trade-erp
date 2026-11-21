package com.zhul.erp.modules.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.inventory.constants.InvConstants;
import com.zhul.erp.modules.inventory.entity.InvBatchDO;
import com.zhul.erp.modules.inventory.service.InventoryPosting;
import com.zhul.erp.modules.inventory.service.InventoryPosting.Doc;
import com.zhul.erp.modules.inventory.service.InventoryPosting.Line;
import com.zhul.erp.modules.inventory.service.InventoryPosting.Loc;
import com.zhul.erp.modules.inventory.service.InventoryWarehouses;
import com.zhul.erp.modules.inventory.service.InventoryWarehouses.Price;
import com.zhul.erp.modules.sales.SalesContractSupport;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** 库存三级账（openspec inventory-category-ledger tasks 3.2–3.5，spec warehouse/inventory「库存台账」、finance/period-close「月结快照」） */
class InventoryCategoryLedgerContractTest extends SalesContractSupport {

    private static final String P = "/api/v1/finance/periods";
    private static final String LEDGER = "/api/v1/warehouse/ledger";

    @Autowired private InventoryPosting posting;
    @Autowired private InventoryWarehouses warehouses;
    @Autowired private TransactionTemplate tx;

    private final YearMonth m1 = YearMonth.now().minusMonths(2);
    private long catA;
    private long catA1;
    private long catB;
    private long pA;
    private long pB;
    private long pNone;

    private long category(String code, String name, Long parent) {
        jdbc.update("insert into product_category (tenant_id, category_code, category_name, category_name_zh, parent_id) "
                + "select 0, ?, ?, ?, ? from dual where not exists (select 1 from product_category where tenant_id = 0 and category_code = ?)",
                code, code, name, parent, code);
        return jdbc.queryForObject("select id from product_category where tenant_id = 0 and category_code = ?", Long.class, code);
    }

    private Loc stockIn(long product, String batchNo, int qty, String price) {
        Loc[] out = new Loc[1];
        TenantContext.runAs(0, () -> tx.executeWithoutResult(s -> {
            InvBatchDO b = warehouses.newBatch(batchNo, product, "", InvConstants.BATCH_PURCHASE, 1L, 1L, null, null,
                    m1.atDay(5), Price.cny(new BigDecimal(price)));
            out[0] = new Loc(warehouses.own().getId(), product, b.getId(), InvConstants.GOOD);
            posting.post(List.of(Line.of(out[0], InvConstants.OP_IN, qty, null,
                    Doc.of(InvConstants.BIZ_PURCHASE_IN, "GR", 1L, batchNo).date(m1.atDay(5)))));
        }));
        return out[0];
    }

    /**
     * m1：A（挂二级品类 A1，上级为一级 A）入库 4 × 710 = 2,840.00；B（一级 B）入库 2 × 100、报废 1 个，期末 100.00；
     * 没有品类的商品入库 1 × 50。
     */
    @BeforeEach
    void stock() {
        jdbc.update("update product_category set deleted_at = null where tenant_id = 0 and category_code like 'test_cl_%'");
        catA = category("test_cl_a", "测试一级A", null);
        catA1 = category("test_cl_a1", "测试二级A1", catA);
        catB = category("test_cl_b", "测试一级B", null);
        pA = manualProduct("CL-TEST-A");
        pB = manualProduct("CL-TEST-B");
        pNone = manualProduct("CL-TEST-NONE");
        jdbc.update("update product set category_id = ? where id = ?", catA1, pA);
        jdbc.update("update product set category_id = ? where id = ?", catB, pB);
        jdbc.update("update product set category_id = 0 where id = ?", pNone);
        stockIn(pA, "GR-CL-A", 4, "710");
        Loc b = stockIn(pB, "GR-CL-B", 2, "100");
        stockIn(pNone, "GR-CL-N", 1, "50");
        TenantContext.runAs(0, () -> tx.executeWithoutResult(s -> posting.post(List.of(Line.of(b, InvConstants.OP_OUT, 1, null,
                Doc.of(InvConstants.BIZ_OTHER_OUT, "OO", 1L, "OO-CL").date(m1.atDay(10)))))));
    }

    private JsonNode category(YearMonth m, Long warehouseId) throws Exception {
        String body = "{\"month\":\"" + m + "\"" + (warehouseId == null ? "" : ",\"warehouseId\":" + warehouseId) + "}";
        return ok(call(json(post(LEDGER + "/category"), body), admin));
    }

    private JsonNode detail(YearMonth m, long categoryId) throws Exception {
        return ok(call(json(post(LEDGER + "/page"), "{\"month\":\"" + m + "\",\"categoryId\":" + categoryId + "}"), admin));
    }

    private static Map<String, JsonNode> byName(JsonNode rows) {
        Map<String, JsonNode> out = new HashMap<>();
        rows.forEach(r -> out.put(r.path("categoryName").asText(), r));
        return out;
    }

    private static void money(String expected, JsonNode v, String what) {
        assertEquals(0, new BigDecimal(expected).compareTo(v.decimalValue()), what + "：" + v);
    }

    @Test
    void 类目账按一级品类汇总_合计等于总账与明细账() throws Exception {
        JsonNode c = category(m1, null);
        JsonNode rows = c.path("rows");
        assertEquals(3, rows.size(), rows.toString());
        assertEquals("测试一级A", rows.get(0).path("categoryName").asText(), "按期末金额倒序");
        Map<String, JsonNode> r = byName(rows);
        money("2840.00", r.get("测试一级A").path("closingAmount"), "二级品类的商品归到上级");
        assertEquals(catA, r.get("测试一级A").path("categoryId").asLong());
        JsonNode b = r.get("测试一级B");
        money("200.00", b.path("inAmount"), "B 入库");
        money("100.00", b.path("outAmount"), "B 报废出库");
        money("100.00", b.path("closingAmount"), "B 期末");
        JsonNode none = r.get("未分类");
        assertEquals(0L, none.path("categoryId").asLong());
        money("50.00", none.path("closingAmount"), "没有品类的商品");

        JsonNode total = c.path("total");
        assertEquals(3, total.path("productCount").asInt());
        money("3090.00", total.path("inAmount"), "合计入库");
        money("2990.00", total.path("closingAmount"), "合计期末");
        money("0.00", total.path("cogsAmount"), "全部仓库带销售成本");
        JsonNode period = ok(call(get(P + "/" + m1), admin));
        for (String f : List.of("openingAmount", "inAmount", "outAmount", "closingAmount")) {
            money(period.path(f).decimalValue().toPlainString(), total.path(f), "总账与库存月结一致 " + f);
        }
        money(period.path("stockAdjustAmount").decimalValue().toPlainString(), total.path("adjustAmount"), "在库成本调整");

        // 下钻：明细账按一级品类筛选，合计等于类目账这一行；0 为未分类
        JsonNode a = detail(m1, catA);
        assertEquals(1, a.path("total").asInt());
        assertEquals(pA, a.path("records").get(0).path("productId").asLong());
        assertEquals("测试一级A", a.path("records").get(0).path("categoryL1Name").asText());
        assertEquals(pNone, detail(m1, 0L).path("records").get(0).path("productId").asLong());

        // 按仓库：没有销售成本，合计仍等于该仓库明细账
        long[] ownId = new long[1];
        TenantContext.runAs(0, () -> ownId[0] = warehouses.own().getId());
        JsonNode own = category(m1, ownId[0]);
        assertTrue(own.path("total").path("cogsAmount").isMissingNode() || own.path("total").path("cogsAmount").isNull(), own.toString());
        money("2990.00", own.path("total").path("closingAmount"), "公司仓库合计");
    }

    @Test
    void 品类已删除的商品归未分类_实时与筛选一致() throws Exception {
        jdbc.update("update product_category set deleted_at = now() where id = ?", catB);
        Map<String, JsonNode> r = byName(category(m1, null).path("rows"));
        assertTrue(!r.containsKey("测试一级B"), r.keySet().toString());
        money("150.00", r.get("未分类").path("closingAmount"), "B 与没有品类的商品");
        assertEquals(2, r.get("未分类").path("productCount").asInt());
        assertEquals(2, detail(m1, 0L).path("total").asInt(), "筛选未分类也包含品类已删除的商品");
    }

    @Test
    void 结账后商品改品类_已结月份不变_之后的月份按新品类() throws Exception {
        ok(call(post(P + "/" + m1 + "/close"), admin));
        jdbc.update("update product set category_id = ? where id = ?", catB, pA);

        Map<String, JsonNode> closed = byName(category(m1, null).path("rows"));
        money("2840.00", closed.get("测试一级A").path("closingAmount"), "已结月份按结账时的品类");
        money("100.00", closed.get("测试一级B").path("closingAmount"), "B 不变");
        assertEquals(pA, detail(m1, catA).path("records").get(0).path("productId").asLong(), "快照按结账时的品类筛选");

        Map<String, JsonNode> now = byName(category(YearMonth.now(), null).path("rows"));
        assertTrue(!now.containsKey("测试一级A"), now.keySet().toString());
        money("2940.00", now.get("测试一级B").path("openingAmount"), "A 的期初计入新品类");
        assertEquals(2, now.get("测试一级B").path("productCount").asInt());
    }

    @Test
    void 导出_月结第一张表是类目账_台账页可导出类目账() throws Exception {
        byte[] month = perform(post(P + "/" + m1 + "/export"), admin).getContentAsByteArray();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(month))) {
            assertEquals("类目账", wb.getSheetName(0));
            assertEquals("库存台账", wb.getSheetName(1));
            assertEquals("销售成本", wb.getSheetName(2));
            assertEquals("总账合计", wb.getSheetAt(0).getRow(4).getCell(0).getStringCellValue());
            assertEquals(2990.00, wb.getSheetAt(0).getRow(4).getCell(5).getNumericCellValue(), 0.001);
        }
        byte[] view = perform(json(post(LEDGER + "/export"), "{\"month\":\"" + m1 + "\",\"view\":\"category\"}"), admin)
                .getContentAsByteArray();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(view))) {
            assertEquals("类目账", wb.getSheetName(0));
            assertEquals("测试一级A", wb.getSheetAt(0).getRow(1).getCell(0).getStringCellValue());
        }
    }
}
