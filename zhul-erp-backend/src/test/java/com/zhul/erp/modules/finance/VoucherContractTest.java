package com.zhul.erp.modules.finance;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.inventory.constants.InvConstants;
import com.zhul.erp.modules.inventory.entity.InvBatchDO;
import com.zhul.erp.modules.inventory.service.InventoryPosting;
import com.zhul.erp.modules.inventory.service.InventoryPosting.CostMove;
import com.zhul.erp.modules.inventory.service.InventoryPosting.Doc;
import com.zhul.erp.modules.inventory.service.InventoryPosting.Line;
import com.zhul.erp.modules.inventory.service.InventoryPosting.Loc;
import com.zhul.erp.modules.inventory.service.InventoryWarehouses;
import com.zhul.erp.modules.inventory.service.InventoryWarehouses.Price;
import com.zhul.erp.modules.sales.SalesContractSupport;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** 业务凭证（openspec inventory-vouchers tasks 3.2–3.7，spec finance/business-voucher） */
class VoucherContractTest extends SalesContractSupport {

    private static final String P = "/api/v1/finance/periods";
    private static final String V = "/api/v1/finance/vouchers";

    @Autowired private InventoryPosting posting;
    @Autowired private InventoryWarehouses warehouses;
    @Autowired private TransactionTemplate tx;

    private final YearMonth m1 = YearMonth.now().minusMonths(2);
    private long supplier;
    private Loc loc;

    /**
     * m1：5 日采购入库 4 个 × 710（GR-V），10 日报废 1 个（OO-V），12 日冲销入库 1 个（GR-V），
     * 25 日外币付款成本调整 CA-V：在库 +12、已出运 +18。期末 = 2840 − 710 − 710 + 12 = 1432。
     */
    @BeforeEach
    void data() {
        long product = manualProduct("6ES7 315-2AH14-0AB0");
        supplier = supplier("凭证供应商甲", null);
        jdbc.update("insert into purchase_order (tenant_id, po_no, supplier_id) values (0, 'PO-V', ?)", supplier);
        long po = jdbc.queryForObject("select max(id) from purchase_order where tenant_id = 0", Long.class);
        jdbc.update("insert into purchase_order_item (tenant_id, po_id) values (0, ?)", po);
        long poItem = jdbc.queryForObject("select max(id) from purchase_order_item where tenant_id = 0", Long.class);
        jdbc.update("insert into inv_other_outbound (tenant_id, outbound_no, outbound_type, warehouse_id, outbound_date, status)"
                + " values (0, 'OO-V', ?, 0, ?, 1)", InvConstants.OO_SCRAP, m1.atDay(10));
        long oo = jdbc.queryForObject("select max(id) from inv_other_outbound where tenant_id = 0", Long.class);
        TenantContext.runAs(0, () -> tx.executeWithoutResult(s -> {
            InvBatchDO b = warehouses.newBatch("GR-V-1", product, "", InvConstants.BATCH_PURCHASE, 1L, 1L, poItem, null,
                    m1.atDay(5), Price.cny(new BigDecimal("710")));
            loc = new Loc(warehouses.own().getId(), product, b.getId(), InvConstants.GOOD);
            posting.post(List.of(Line.of(loc, InvConstants.OP_IN, 4, null,
                    Doc.of(InvConstants.BIZ_PURCHASE_IN, "GR", 1L, "GR-V").date(m1.atDay(5)))));
            posting.post(List.of(Line.of(loc, InvConstants.OP_OUT, 1, null,
                    Doc.of(InvConstants.BIZ_OTHER_OUT, "OO", oo, "OO-V").date(m1.atDay(10)))));
            posting.post(List.of(Line.of(loc, InvConstants.OP_OUT, 1, null,
                    Doc.of(InvConstants.BIZ_PURCHASE_IN_REVERSE, "GR", 1L, "GR-V").date(m1.atDay(12)))));
            posting.postCostAdjust(List.of(new CostMove(loc, new BigDecimal("713"), new BigDecimal("12.00"), BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, Doc.of(InvConstants.BIZ_COST_ADJUST, "CA", 1L, "CA-V"))));
            // 成本调整流水记当天；这里把它挪回 m1（真实数据里调整单日期与流水同在未结账月份）
            jdbc.update("update inv_move set biz_date = ?, move_time = ? where tenant_id = 0 and op_type = ?",
                    m1.atDay(25), m1.atDay(25).atTime(10, 0), InvConstants.OP_COST_ADJUST);
            jdbc.update("insert into inv_cost_adjust (tenant_id, adjust_no, adjust_date, period_month, reason) values (0, 'CA-V', ?, ?, ?)",
                    m1.atDay(25), m1.toString(), InvConstants.CA_PAYMENT);
            long ca = jdbc.queryForObject("select max(id) from inv_cost_adjust where tenant_id = 0", Long.class);
            String item = "insert into inv_cost_adjust_item (tenant_id, adjust_id, batch_id, product_id, part, doc_type, amount_cny)"
                    + " values (0, ?, ?, ?, ?, ?, ?)";
            jdbc.update(item, ca, b.getId(), product, InvConstants.PART_STOCK, "", new BigDecimal("12.00"));
            jdbc.update(item, ca, b.getId(), product, InvConstants.PART_SHIPPED, "SH", new BigDecimal("18.00"));
        }));
    }

    private JsonNode close() throws Exception {
        return ok(call(post(P + "/" + m1 + "/close"), admin));
    }

    private JsonNode detail() throws Exception {
        return ok(call(get(V + "/months/" + m1), admin));
    }

    private static JsonNode find(JsonNode vouchers, String summaryStart) {
        for (JsonNode v : vouchers) {
            if (v.path("summary").asText().startsWith(summaryStart)) {
                return v;
            }
        }
        throw new AssertionError("没有凭证：" + summaryStart + " in " + vouchers);
    }

    private static void entry(JsonNode v, int i, String code, String partner, String debit, String credit) {
        JsonNode e = v.path("entries").get(i);
        assertEquals(code, e.path("accountCode").asText(), v.toString());
        assertEquals(partner == null ? "" : partner, e.path("partnerName").asText(""), v.toString());
        if (debit != null) {
            money(debit, e.path("debit"));
        }
        if (credit != null) {
            money(credit, e.path("credit"));
        }
    }

    @Test
    void 结账按单据生成凭证_借贷平衡_与库存账对得上() throws Exception {
        close();
        JsonNode d = detail();
        assertTrue(d.path("generated").asBoolean());
        JsonNode vs = d.path("vouchers");
        assertEquals(4, vs.size(), vs.toString());

        JsonNode in = find(vs, "采购入库 GR-V");
        assertEquals("采购入库 GR-V 凭证供应商甲", in.path("summary").asText());
        assertEquals(m1.atDay(5).toString(), in.path("voucherDate").asText());
        assertEquals("采购入库", in.path("action").asText());
        assertEquals("凭证供应商甲", in.path("partnerText").asText());
        entry(in, 0, "1405", null, "2840.00", null);
        entry(in, 1, "2202", "凭证供应商甲", null, "2840.00");

        JsonNode red = find(vs, "冲销采购入库 GR-V");
        entry(red, 0, "1405", null, "-710.00", null);
        entry(red, 1, "2202", "凭证供应商甲", null, "-710.00");

        JsonNode oo = find(vs, "其他出库 · 报废 OO-V");
        entry(oo, 0, "6711", null, "710.00", null);
        entry(oo, 1, "1405", null, null, "710.00");

        JsonNode ca = find(vs, "成本调整 CA-V");
        assertEquals("成本调整 CA-V 外币付款 凭证供应商甲", ca.path("summary").asText());
        entry(ca, 0, "1405", null, "12.00", null);
        entry(ca, 1, "6401", null, "18.00", null);
        entry(ca, 2, "2202", "凭证供应商甲", null, "30.00");

        // 期初 0 + 库存商品净额（2840 − 710 − 710 + 12）+ 不生成凭证 0 = 期末 1432
        JsonNode r = d.path("recon");
        money("0.00", r.path("openingAmount"));
        money("1432.00", r.path("stockNetAmount"));
        money("0.00", r.path("noVoucherAmount"));
        money("1432.00", r.path("closingAmount"));
        money("0.00", r.path("diff"));
        money("2870.00", d.path("debitTotal"));
        assertEquals(0, d.path("missingItems").size());

        JsonNode months = ok(call(get(V + "/months"), admin));
        assertEquals(m1.toString(), months.get(0).path("month").asText());
        assertEquals(4, months.get(0).path("voucherCount").asInt());
        money("0.00", months.get(0).path("reconDiff"));
    }

    @Test
    void 导出_重复导出要确认_缺科目不能导出() throws Exception {
        close();
        MockHttpServletResponse first = perform(post(V + "/months/" + m1 + "/export"), admin);
        assertEquals(200, first.getStatus());
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(first.getContentAsByteArray()))) {
            Sheet s = wb.getSheetAt(0);
            assertEquals("凭证日期", s.getRow(0).getCell(0).getStringCellValue());
            assertEquals(1 + 2 + 2 + 2 + 3, s.getPhysicalNumberOfRows(), "表头 + 4 张凭证 9 行分录");
            assertEquals("记", s.getRow(1).getCell(1).getStringCellValue());
            assertEquals("ITSUP" , s.getRow(2).getCell(8).getStringCellValue().replaceAll("\\d", ""), "往来单位编码默认用供应商编码");
        }
        JsonNode d = detail();
        assertEquals(1, d.path("exportCount").asInt());
        assertFalse(d.path("exportedByName").asText().isEmpty());

        JsonNode again = fail(call(post(V + "/months/" + m1 + "/export"), admin));
        assertEquals("VOUCHER_EXPORT_CONFIRM", again.path("data").path("errorCode").asText());
        assertEquals(200, perform(post(V + "/months/" + m1 + "/export?confirmed=true"), admin).getStatus());
        assertEquals(2, detail().path("exportCount").asInt());

        // 设置往来单位编码后导出用它；清空库存商品科目后不能导出
        ok(call(json(put(V + "/suppliers/" + supplier), write(Map.of("partnerCode", "T-0099"))), admin));
        assertEquals("T-0099", ok(call(get(V + "/suppliers"), admin)).get(0).path("partnerCode").asText());
        ok(call(json(put(V + "/accounts"), write(Map.of("items", List.of(Map.of("itemKey", "STOCK", "accountCode", "",
                "accountName", ""))))), admin));
        JsonNode missing = fail(call(post(V + "/months/" + m1 + "/export?confirmed=true"), admin));
        assertEquals("VOUCHER_ACCOUNT_MISSING", missing.path("data").path("errorCode").asText());
        ok(call(json(put(V + "/accounts"), write(Map.of("items", List.of(Map.of("itemKey", "STOCK", "accountCode", "140501",
                "accountName", "库存商品-工控"))))), admin));
        assertEquals("140501", find(detail().path("vouchers"), "采购入库").path("entries").get(0).path("accountCode").asText(),
                "改科目不用重新生成");
    }

    @Test
    void 反结账作废凭证_重新结账重新生成() throws Exception {
        close();
        perform(post(V + "/months/" + m1 + "/export"), admin);
        assertTrue(ok(call(get(P + "/" + m1), admin)).path("voucherExported").asBoolean());
        ok(call(json(post(P + "/" + m1 + "/reopen"), write(Map.of("reason", "补登单据"))), admin));
        assertEquals(0, jdbc.queryForObject("select count(*) from fin_voucher where tenant_id = 0 and period_month = ?"
                + " and status = 1 and deleted_at is null", Integer.class, m1.toString()));
        assertEquals(4, jdbc.queryForObject("select count(*) from fin_voucher where tenant_id = 0 and period_month = ?"
                + " and status = 2", Integer.class, m1.toString()), "作废留痕，不物理删除");
        close();
        JsonNode d = detail();
        assertEquals(4, d.path("vouchers").size());
        assertEquals(0, d.path("exportCount").asInt(), "重新生成后导出次数从 0 开始");
    }

    @Test
    void 上线前已结账的月份_手动生成一次() throws Exception {
        close();
        // 模拟上线前结账：没有凭证
        jdbc.update("update fin_voucher set status = 2, deleted_at = now() where tenant_id = 0 and period_month = ?", m1.toString());
        jdbc.update("update fin_voucher_month set deleted_at = now() where tenant_id = 0 and period_month = ?", m1.toString());
        JsonNode months = ok(call(get(V + "/months"), admin));
        assertFalse(months.get(0).path("generated").asBoolean());
        assertEquals(4, ok(call(post(V + "/months/" + m1 + "/generate"), admin)).path("vouchers").size());
        assertEquals(m1 + " 已经有凭证了", fail(call(post(V + "/months/" + m1 + "/generate"), admin)).path("message").asText());
        String open = YearMonth.now().toString();
        assertTrue(fail(call(post(V + "/months/" + open + "/generate"), admin)).path("message").asText().contains("还没结账"));
    }

    /** 随机组合的入库、出运、撤销、退货入库、其他出库、作废、冲销、期初补录，结账后核对算式恒成立（task 3.4） */
    @Test
    void 随机流水下核对算式恒成立() throws Exception {
        java.util.Random rnd = new java.util.Random(20261122L);
        int[] stock = {2};
        TenantContext.runAs(0, () -> tx.executeWithoutResult(s -> {
            for (int i = 0; i < 40; i++) {
                java.time.LocalDate day = m1.atDay(1 + rnd.nextInt(m1.lengthOfMonth()));
                int qty = 1 + rnd.nextInt(3);
                int pick = rnd.nextInt(8);
                boolean out = pick >= 4;
                if (out && stock[0] < qty) {
                    pick = rnd.nextInt(4);
                    out = false;
                }
                String biz = switch (pick) {
                    case 0 -> InvConstants.BIZ_PURCHASE_IN;
                    case 1 -> InvConstants.BIZ_SALES_OUT_UNDO;
                    case 2 -> InvConstants.BIZ_OTHER_OUT_VOID;
                    case 3 -> InvConstants.BIZ_OPENING;
                    case 4 -> InvConstants.BIZ_SALES_OUT;
                    case 5 -> InvConstants.BIZ_OTHER_OUT;
                    case 6 -> InvConstants.BIZ_PURCHASE_IN_REVERSE;
                    default -> InvConstants.BIZ_PURCHASE_RETURN_OUT;
                };
                String type = switch (biz) {
                    case InvConstants.BIZ_SALES_OUT, InvConstants.BIZ_SALES_OUT_UNDO -> "SH";
                    case InvConstants.BIZ_OTHER_OUT, InvConstants.BIZ_OTHER_OUT_VOID -> "OO";
                    case InvConstants.BIZ_PURCHASE_RETURN_OUT -> "PR";
                    case InvConstants.BIZ_OPENING -> "OPENING";
                    default -> "GR";
                };
                posting.post(List.of(Line.of(loc, out ? InvConstants.OP_OUT : InvConstants.OP_IN, qty, null,
                        Doc.of(biz, type, 100L + rnd.nextInt(5), type + "-R" + i).date(day))));
                stock[0] += out ? -qty : qty;
            }
        }));
        close();
        JsonNode d = detail();
        JsonNode r = d.path("recon");
        money("0.00", r.path("diff"));
        BigDecimal sum = r.path("openingAmount").decimalValue().add(r.path("stockNetAmount").decimalValue())
                .add(r.path("noVoucherAmount").decimalValue());
        assertEquals(0, sum.compareTo(r.path("closingAmount").decimalValue()), r.toString());
        for (JsonNode v : d.path("vouchers")) {
            BigDecimal dr = BigDecimal.ZERO;
            BigDecimal cr = BigDecimal.ZERO;
            for (JsonNode e : v.path("entries")) {
                dr = dr.add(e.path("debit").decimalValue());
                cr = cr.add(e.path("credit").decimalValue());
            }
            assertEquals(0, dr.compareTo(cr), "借贷平衡：" + v.path("summary").asText());
        }
    }

    /** 只有查看权限：能看月份，不能导出、生成、改科目（task 3.5） */
    @Test
    void 没有按钮权限不能导出与改科目() throws Exception {
        close();
        jdbc.update("insert into role_resource (role_code, resource_id, resource_code) values (?, 100131, '')", BUYER_ROLE);
        String viewer = token("it_lin");
        assertEquals(m1.toString(), ok(call(get(V + "/months"), viewer)).get(0).path("month").asText());
        assertEquals(403, perform(post(V + "/months/" + m1 + "/export"), viewer).getStatus());
        assertEquals(403, perform(json(put(V + "/accounts"), write(Map.of("items", List.of()))), viewer).getStatus());
        assertEquals(403, perform(json(put(V + "/suppliers/" + supplier), write(Map.of("partnerCode", "X"))), viewer).getStatus());
        assertEquals(403, perform(get(V + "/months"), token("it_wang")).getStatus(), "没有菜单权限看不到");
    }
}
