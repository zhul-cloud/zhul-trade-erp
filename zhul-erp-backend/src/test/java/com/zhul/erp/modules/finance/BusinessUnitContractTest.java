package com.zhul.erp.modules.finance;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.modules.sales.SalesContractSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * spec system/business-unit、access/data-scope（统计按事业部）、system/bank-account、sales/payment-receipt、
 * finance/fx-settlement、finance/expense、finance/operating-report、finance/profit-stats（事业部核算）
 */
class BusinessUnitContractTest extends SalesContractSupport {

    private static final String BU = "/api/v1/system/business-units";
    private static final String DEPT = "/api/v1/system/depts";
    private static final String FE = "/api/v1/finance/expenses";
    private static final String REPORT = "/api/v1/finance/operating-report";
    private static final String PROFIT = "/api/v1/finance/profit-stats";
    private static final String FX = "/api/v1/finance/fx-settlements";
    private static final String BANK = "/api/v1/system/bank-accounts";
    private static final long PARTNER = 99000041L;
    private static final String PARTNER_ROLE = "IT_BU_PTNR";

    private int deptA;
    private int deptA1;
    private int deptB;

    @BeforeEach
    void depts() {
        cleanupUnits();
        deptA = dept("IT-外贸部门-阿里国际站", 0);
        deptA1 = dept("IT-业务组-阿里国际站", deptA);
        deptB = dept("IT-外贸部门-中国制造网", 0);
    }

    @AfterEach
    void cleanupUnits() {
        jdbc.update("delete from role_org where role_code = ?", PARTNER_ROLE);
        jdbc.update("delete from role_resource where role_code = ?", PARTNER_ROLE);
        jdbc.update("delete from role where code = ?", PARTNER_ROLE);
        jdbc.update("delete from account where id = ?", PARTNER);
        jdbc.update("delete from user_basic where id = ?", PARTNER);
        jdbc.update("delete from department where tenant_id = 0 and name like 'IT-%'");
        jdbc.update("update department set is_business_unit = 0, bu_start_month = null where tenant_id = 0");
        jdbc.update("delete from sys_config where tenant_id = 0 and config_key = 'finance.bu.min-share-percent'");
        jdbc.update("insert into sys_config (tenant_id, config_key, config_name, config_value, config_type, is_builtin, is_encrypted, "
                + "config_group, remark) values (0, 'finance.bu.min-share-percent', '事业部最低分摊比例（%）', '20', 'INTEGER', 1, 0, "
                + "'finance', '')");
        jdbc.update("delete from customer where tenant_id = 0");
    }

    private int dept(String name, int pid) {
        jdbc.update("insert into department (tenant_id, pid, name, level, status) values (0, ?, ?, 1, 1)", pid, name);
        int id = jdbc.queryForObject("select max(id) from department where tenant_id = 0", Integer.class);
        jdbc.update("update department set code = ? where id = ?", "DP" + id, id);
        return id;
    }

    private JsonNode mark(int deptId, boolean on, String month) throws Exception {
        Map<String, Object> b = new HashMap<>();
        b.put("enabled", on);
        b.put("startMonth", month);
        return call(json(put(DEPT + "/" + deptId + "/business-unit"), write(b)), admin);
    }

    private Map<String, Object> expense(String name, String amount, Integer unit) {
        Map<String, Object> b = new HashMap<>();
        b.put("name", name);
        b.put("category", "RENT");
        b.put("amortizeMonths", 1);
        b.put("occurDate", LocalDate.now().toString());
        b.put("payeeType", 4);
        b.put("payeeName", "房东");
        b.put("currencyCode", "CNY");
        b.put("amount", amount);
        b.put("method", 1);
        b.put("allocations", List.of());
        b.put("businessUnitId", unit);
        return b;
    }

    /** 某事业部客户的一张已发送 PI，收款账户取该事业部的美元账户；平台收款后转成订单 */
    private long paidOrder(String customer, int unit, String platformNo) throws Exception {
        long c = customer(customer, "Germany");
        jdbc.update("update customer set business_unit_id = ? where id = ?", unit, c);
        JsonNode pi = ok(createPi(quotationItemIds(quotation(c, 2, "USD", null, l("6ES7214-1AG40-0XB0", 1, "1000", "1000.00")))));
        long id = pi.path("id").asLong();
        assertEquals(unit, jdbc.queryForObject("select business_unit_id from proforma_invoice where id = ?", Integer.class, id),
                "PI 跟随客户的事业部");
        Map<String, Object> body = saveBody(pi);
        body.put("bankAccountId", usdAccount(unit));
        ok(savePi(id, body));
        ok(send(id));
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("paymentMethod", "ALIBABA_TA");
        r.put("platformOrderNo", platformNo);
        r.put("amount", "1000.00");
        r.put("platformFee", "0");
        r.put("receiptDate", LocalDate.now().toString());
        ok(call(json(post(PI + "/" + id + "/platform-receipts"), write(r)), admin));
        ok(call(json(post(PI + "/" + id + "/convert"), "{}"), admin));
        return jdbc.queryForObject("select id from sales_order where pi_id = ?", Long.class, id);
    }

    private int usdAccount(int unit) {
        return jdbc.queryForObject("select min(id) from tenant_bank_account where tenant_id = 0 and currency_code = 'USD' "
                + "and business_unit_id = ?", Integer.class, unit);
    }

    private int account(String currency, int unit) throws Exception {
        Map<String, Object> b = new HashMap<>();
        b.put("currencyCode", currency);
        b.put("accountType", 1);
        b.put("bankName", "Citibank N.A., Hong Kong");
        b.put("accountName", "Fuzhou Fouwell Technology Co., Ltd.");
        b.put("accountNo", "6222000011112290");
        b.put("businessUnitId", unit);
        return ok(call(json(post(BANK), write(b)), admin)).path("id").asInt();
    }

    private String partnerOf(int unit) {
        jdbc.update("insert into role (tenant_id, code, name, permission_scope, status) values (0, ?, '合伙人', 2, 1)", PARTNER_ROLE);
        jdbc.update("insert into role_org (role_code, org_code) values (?, ?)", PARTNER_ROLE, "DP" + unit);
        for (int menu : new int[] {100112, 100109}) {
            jdbc.update("insert into role_resource (role_code, resource_id, resource_code) values (?, ?, '')", PARTNER_ROLE, menu);
        }
        jdbc.update("insert into user_basic (id, tenant_id, name, username, role_code, dept_id, status) values (?, 0, '陈合伙人', 'it_bu_partner', ?, ?, 1)",
                PARTNER, PARTNER_ROLE, unit);
        jdbc.update("insert into account (id, tenant_id, user_id, username, admin_flag) values (?, 0, ?, 'it_bu_partner', 0)", PARTNER, PARTNER);
        return token("it_bu_partner");
    }

    @Test
    void withoutUnitsEverythingStaysCompanyWide() throws Exception {
        JsonNode view = ok(call(get(BU), admin));
        assertFalse(view.path("enabled").asBoolean());
        assertEquals(20, view.path("minSharePercent").asInt());
        JsonNode fee = ok(call(json(post(FE), write(expense("房租", "3000", 12345))), admin));
        assertTrue(fee.path("businessUnitId").isMissingNode() || fee.path("businessUnitId").isNull(), "没有事业部时忽略所选事业部");
        JsonNode report = ok(call(get(REPORT), admin));
        assertTrue(report.path("businessUnitId").isMissingNode() || report.path("businessUnitId").isNull());
        money("3000.00", report.path("total").path("opexTotal"));
    }

    @Test
    void markUnits_backfill_allocation_partnerScope_accounts_fx() throws Exception {
        // 标记前的单据：一个客户、一张费用单、一个美元账户
        long oldCustomer = customer("Old Customer GmbH", "Germany");
        JsonNode oldFee = ok(call(json(post(FE), write(expense("旧房租", "1200", null))), admin));
        bankAccount("USD");

        // 第一个事业部：已有单据全部归入；不能嵌套；有单据的不能取消
        String thisMonth = YearMonth.now().toString();
        ok(mark(deptA, true, YearMonth.now().withMonth(1).toString()));
        assertEquals(deptA, jdbc.queryForObject("select business_unit_id from customer where id = ?", Integer.class, oldCustomer));
        assertEquals(deptA, jdbc.queryForObject("select business_unit_id from fin_expense where id = ?", Integer.class,
                oldFee.path("id").asLong()));
        assertEquals(deptA, jdbc.queryForObject("select business_unit_id from tenant_bank_account where tenant_id = 0", Integer.class));
        // 只有一个事业部时，公共部门的人登记客户不用选，直接归它
        long single = ok(call(json(post("/api/v1/masterdata/customers"),
                write(Map.of("name", "Solo Automation AB", "country", "Sweden"))), admin)).path("id").asLong();
        assertEquals(deptA, jdbc.queryForObject("select business_unit_id from customer where id = ?", Integer.class, single));
        assertEquals("上级部门「IT-外贸部门-阿里国际站」已是事业部，事业部不能嵌套",
                fail(mark(deptA1, true, thisMonth)).path("message").asText());
        ok(mark(deptB, true, thisMonth));
        assertEquals(deptA, jdbc.queryForObject("select business_unit_id from customer where id = ?", Integer.class, oldCustomer),
                "第二个事业部不接收已有单据");
        assertEquals("该事业部已有单据，不能取消事业部标记", fail(mark(deptA, false, null)).path("message").asText());
        JsonNode view = ok(call(get(BU), admin));
        assertTrue(view.path("enabled").asBoolean());
        assertEquals(2, view.path("units").size());
        assertTrue(view.path("statsTotal").asBoolean(), "管理员能看合计");
        assertEquals("最低分摊比例 × 事业部数不能超过 100%（当前 2 个事业部，最多 50%）",
                fail(call(json(put(BU + "/min-share"), "{\"percent\":60}"), admin)).path("message").asText());

        // 公共部门的人登记客户须选事业部
        assertEquals("负责人不属于任何事业部，请选择客户所属的事业部", fail(call(json(post("/api/v1/masterdata/customers"),
                write(Map.of("name", "Nordic Automation AB", "country", "Sweden"))), admin)).path("message").asText());

        // 收款账户：按「事业部 + 币种」各有默认；公司有事业部时须选事业部
        int bUsd = account("USD", deptB);
        assertTrue(ok(call(get(BANK + "/" + bUsd), admin)).path("isDefault").asBoolean(), "中国制造网的第一个美元账户成为它的默认");
        assertEquals(1, jdbc.queryForObject("select count(*) from tenant_bank_account where tenant_id = 0 and is_default = 1 "
                + "and business_unit_id = ?", Integer.class, deptA), "阿里的美元默认不受影响");

        // 两个事业部各一张订单（收入相同）
        long soA = paidOrder("Ali Buyer GmbH", deptA, "TA-A-0001");
        long soB = paidOrder("MIC Buyer GmbH", deptB, "TA-B-0001");
        assertEquals(deptB, jdbc.queryForObject("select business_unit_id from sales_order where id = ?", Integer.class, soB));
        assertEquals(deptB, jdbc.queryForObject("select business_unit_id from payment_receipt where so_id = ? or pi_id = "
                + "(select pi_id from sales_order where id = ?) limit 1", Integer.class, soB, soB), "收款跟随订单");

        // 本月费用：公共 1,000（两边各一半）、中国制造网直接 300
        ok(call(json(post(FE), write(expense("本月房租", "1000", null))), admin));
        ok(call(json(post(FE), write(expense("中国制造网会员费", "300", deptB))), admin));
        assertEquals(1, ok(call(get(FE).param("businessUnitId", "0"), admin)).path("total").asInt(), "按「公共」筛选");

        int m = LocalDate.now().getMonthValue();
        JsonNode total = row(ok(call(get(REPORT), admin)), m);
        JsonNode a = row(ok(call(get(REPORT).param("businessUnitId", String.valueOf(deptA)), admin)), m);
        JsonNode b = row(ok(call(get(REPORT).param("businessUnitId", String.valueOf(deptB)), admin)), m);
        assertEquals(0, new BigDecimal("0.5").compareTo(b.path("sharePercent").decimalValue()), "收入相同，按销售额各 50%");
        money("300.00", b.path("opexDirectTotal"));
        money("500.00", b.path("opexSharedTotal"));
        money("500.00", a.path("opexSharedTotal"));
        BigDecimal sumNet = a.path("net").decimalValue().add(b.path("net").decimalValue());
        assertEquals(0, total.path("net").decimalValue().compareTo(sumNet), "各事业部之和等于全公司");
        BigDecimal sumRevenue = a.path("revenue").decimalValue().add(b.path("revenue").decimalValue());
        assertEquals(0, total.path("revenue").decimalValue().compareTo(sumRevenue));

        // 合伙人只看本事业部
        String partner = partnerOf(deptB);
        JsonNode pv = ok(call(get(BU), partner));
        assertFalse(pv.path("statsTotal").asBoolean());
        assertEquals(List.of(deptB), List.of(pv.path("statsUnitIds").get(0).asInt()));
        assertEquals(1, pv.path("statsUnitIds").size());
        assertEquals(deptB, ok(call(get(REPORT), partner)).path("businessUnitId").asInt(), "不传时默认本事业部");
        assertEquals("无权查看该事业部", fail(call(get(REPORT).param("businessUnitId", String.valueOf(deptA)), partner))
                .path("message").asText());
        assertEquals("无权查看该事业部", fail(call(get(PROFIT).param("businessUnitId", String.valueOf(deptA)), partner))
                .path("message").asText());
        JsonNode profit = ok(call(get(PROFIT), partner));
        assertEquals(1, profit.path("summary").path("orderCount").asInt(), "利润统计只算本事业部的订单");

        // 收错账户：中国制造网的订单收到阿里的账户，计入订单的事业部并标记
        long piB = jdbc.queryForObject("select pi_id from sales_order where id = ?", Long.class, soB);
        long piB2 = ok(createPi(quotationItemIds(quotation(jdbc.queryForObject("select customer_id from sales_order where id = ?",
                Long.class, soB), 2, "USD", null, l("6ES7214-1AG40-0XB0", 1, "500", "500.00"))))).path("id").asLong();
        Map<String, Object> save = saveBody(ok(call(get(PI + "/" + piB2), admin)));
        save.put("bankAccountId", usdAccount(deptA));
        assertEquals("只能选择本事业部的收款账户", fail(savePi(piB2, save)).path("message").asText());
        save.put("bankAccountId", bUsd);
        ok(savePi(piB2, save));
        ok(send(piB2));
        Map<String, Object> rc = new LinkedHashMap<>();
        rc.put("amount", "500.00");
        rc.put("receiptDate", LocalDate.now().toString());
        rc.put("bankAccountId", usdAccount(deptA));
        rc.put("feeDiff", false);
        ok(call(json(post(PI + "/" + piB2 + "/receipts"), write(rc)), admin));
        Map<String, Object> wrong = jdbc.queryForMap("select business_unit_id, bu_mismatch from payment_receipt where pi_id = ? "
                + "and kind = 2", piB2);
        assertEquals(deptB, ((Number) wrong.get("business_unit_id")).intValue());
        assertEquals(1, ((Number) wrong.get("bu_mismatch")).intValue());
        assertTrue(piB > 0);

        // 结汇按事业部各自先进先出：中国制造网的结汇不碰阿里的到账
        int bCny = account("CNY", deptB);
        Map<String, Object> fx = new HashMap<>();
        fx.put("settleDate", LocalDate.now().toString());
        fx.put("currencyCode", "USD");
        fx.put("amount", "2000");
        fx.put("amountCny", "14300");
        fx.put("bankAccountId", bCny);
        fx.put("idempotencyKey", "it-bu-fx-1");
        assertEquals("IT-外贸部门-中国制造网可结汇的USD只有 USD 1,500.00",
                fail(call(json(post(FX), write(fx)), admin)).path("message").asText());
        fx.put("amount", "1500");
        fx.put("amountCny", "10725");
        fx.put("idempotencyKey", "it-bu-fx-2");
        JsonNode s = ok(call(json(post(FX), write(fx)), admin));
        assertEquals(deptB, s.path("businessUnitId").asInt(), "默认取入账账户的事业部");
        assertEquals(0, jdbc.queryForObject("select count(*) from payment_receipt where tenant_id = 0 and business_unit_id = ? "
                + "and settled_amount > 0", Integer.class, deptA), "阿里的到账不受影响");
    }

    private static JsonNode row(JsonNode report, int month) {
        for (JsonNode r : report.path("rows")) {
            if (r.path("month").asInt() == month) {
                return r;
            }
        }
        throw new AssertionError("没有 " + month + " 月");
    }
}
