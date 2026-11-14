package com.zhul.erp.modules.workbench;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.support.TenantContractSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** spec system/workbench：角色选模板、按模板与权限显示组件、多角色切换、组件数据权限、看板组件还没上线时的通用兜底 */
class WorkbenchContractTest extends TenantContractSupport {

    private static final String API = "/api/v1/workbench";
    private static final String ROLE = "IT_ROLE";
    private static final int T = 99721;
    private static final int DASHBOARD = 100001;
    private static final int MY_TASKS = 100055;
    private static final int OPP_STATS = 100071;
    private static final int PI = 100078;
    private static final int REQUIREMENTS = 100087;
    private static final int ORDERS = 100088;
    private static final int SHIPMENTS = 100089;

    @BeforeEach
    void seed() {
        cleanup();
        // 账号、用户属于令牌所在的公司（看板按当前租户查账号与角色）；套餐包含全部菜单，权限只由角色决定
        jdbc.update("insert into tenant_package (id, name, menu_ids) select ?, 'IT工作台套餐', JSON_ARRAYAGG(id) from resource", T);
        jdbc.update("insert into tenant (id, name, package_id) values (?, 'IT工作台租户', ?)", T, T);
        testUserTenant = T;
    }

    @AfterEach
    void cleanup() {
        jdbc.update("delete from tenant where id = ?", T);
        jdbc.update("delete from tenant_package where id = ?", T);
        jdbc.update("delete from role where code in (?, 'IT_ROLE2') or name like 'IT工作台%'", ROLE);
        jdbc.update("delete from account_role where role_code in (?, 'IT_ROLE2')", ROLE);
    }

    private void role(String code, String template) {
        jdbc.update("insert into role (tenant_id, code, name, permission_scope, workbench_template, status) values (0, ?, ?, 3, ?, 1)",
                code, "IT工作台" + code, template);
    }

    private static List<String> keys(JsonNode board) {
        List<String> out = new ArrayList<>();
        board.path("widgets").forEach(w -> out.add(w.path("key").asText()));
        return out;
    }

    @Test
    void purchaserBoard_widgetsFilteredByMenus_andWidgetPermission() throws Exception {
        role(ROLE, "purchaser");
        loginWithResources("it_wb_buyer", DASHBOARD, MY_TASKS, REQUIREMENTS, ORDERS, SHIPMENTS);
        String token = token("it_wb_buyer", T);
        JsonNode bench = ok(call(get(API), token));
        assertEquals(1, bench.path("boards").size(), "只有一个模板时只有一个看板");
        JsonNode board = bench.path("boards").get(0);
        assertEquals("purchaser", board.path("code").asText());
        assertEquals("采购员", board.path("name").asText());
        assertFalse(board.path("fallback").asBoolean());
        assertEquals(List.of("my-inquiry", "quote-stats", "quote-trend", "my-purchase"), keys(board));
        assertEquals("full", board.path("widgets").get(0).path("size").asText());

        JsonNode purchase = ok(call(get(API + "/widgets/my-purchase"), token));
        assertTrue(purchase.has("openRequirements") && purchase.has("pendingShip") && purchase.has("inTransit")
                && purchase.has("discrepancies"), purchase.toString());
        JsonNode inquiry = ok(call(get(API + "/widgets/my-inquiry"), token));
        assertEquals(30, inquiry.path("trend").size());

        // 不在自己看板里的组件、不存在的组件
        assertEquals("没有这个看板组件的权限", fail(call(get(API + "/widgets/overdue-pi"), token)).path("message").asText());
        assertEquals("看板组件不存在", fail(call(get(API + "/widgets/nope"), token)).path("message").asText());

        // 只有采购订单菜单：我的采购只给采购订单的数字
        loginWithResources("it_wb_buyer", DASHBOARD, MY_TASKS, ORDERS);
        JsonNode partial = ok(call(get(API + "/widgets/my-purchase"), token));
        assertTrue(partial.has("pendingShip") && !partial.has("openRequirements") && !partial.has("inTransit"), partial.toString());
        // 没有工作台菜单：接口拒绝
        loginWithResources("it_wb_buyer", MY_TASKS);
        assertEquals(403, perform(get(API), token).getStatus());
    }

    @Test
    void noTemplateOrUnbuiltBoard_fallsBackToGeneral() throws Exception {
        // 角色不在角色表里（没有模板）：通用
        loginWithResources("it_wb_staff", DASHBOARD, OPP_STATS, PI);
        String token = token("it_wb_staff", T);
        JsonNode general = ok(call(get(API), token)).path("boards").get(0);
        assertEquals("general", general.path("code").asText());
        assertEquals(List.of("opportunity-stats", "overdue-pi"), keys(general));

        // 运营负责人看板的组件都没有权限：先显示通用看板的组件，并标出兜底
        role(ROLE, "ops_lead");
        JsonNode boss = ok(call(get(API), token)).path("boards").get(0);
        assertEquals("ops_lead", boss.path("code").asText());
        assertTrue(boss.path("fallback").asBoolean());
        assertEquals(List.of("opportunity-stats", "overdue-pi"), keys(boss));
        // 只用现有接口取数的组件（前端直接调商机统计接口）没有工作台数据接口
        assertEquals("这个组件没有工作台数据接口", fail(call(get(API + "/widgets/opportunity-stats"), token)).path("message").asText());
    }

    @Test
    void multipleRoles_sortedBySeniority_defaultHighest() throws Exception {
        role(ROLE, "purchaser");
        role("IT_ROLE2", "purchase_lead");
        loginWithResources("it_wb_multi", DASHBOARD, MY_TASKS);
        Integer account = jdbc.queryForObject("select id from account where username = 'it_wb_multi'", Integer.class);
        jdbc.update("insert into account_role (account_id, role_code) values (?, 'IT_ROLE2')", account);
        JsonNode bench = ok(call(get(API), token("it_wb_multi", T)));
        List<String> codes = new ArrayList<>();
        bench.path("boards").forEach(b -> codes.add(b.path("code").asText()));
        assertEquals(List.of("purchase_lead", "purchaser"), codes, "负责人在前");
        assertEquals("purchase_lead", bench.path("defaultCode").asText());
    }

    /** 每个岗位看板的全部组件都能取到数据（不报错、至少一块），组件清单与模板一致 */
    @Test
    void everyRoleBoard_widgetsLoad() throws Exception {
        int[] all = {DASHBOARD, MY_TASKS, OPP_STATS, PI, REQUIREMENTS, ORDERS, SHIPMENTS, 100051, 100053, 100054, 100079,
                100100, 100102, 100103, 100107, 100108, 100109, 100112, 100113, 100114, 100116, 100117};
        Map<String, List<String>> expected = Map.of(
                "boss", List.of("biz-overview", "cash", "sales-funnel", "revenue-trend", "risk-alerts", "rankings"),
                "sales_lead", List.of("team-performance", "follow-ups", "opportunity-stats", "sales-compare", "quote-conversion"),
                "sales", List.of("my-todo", "my-performance", "quote-progress", "overdue-pi", "my-opportunities"),
                "purchase_lead", List.of("sourcing-overview", "buyer-efficiency", "purchase-execution", "bargain-result", "payables"),
                "ops_lead", List.of("content-pipeline", "platform-progress", "media-overview", "monthly-output"));
        for (Map.Entry<String, List<String>> e : expected.entrySet()) {
            jdbc.update("delete from role where code = ?", ROLE);
            role(ROLE, e.getKey());
            loginWithResources("it_wb_all", all);
            String token = token("it_wb_all", T);
            JsonNode board = ok(call(get(API), token)).path("boards").get(0);
            assertEquals(e.getKey(), board.path("code").asText());
            assertFalse(board.path("fallback").asBoolean(), e.getKey() + " 的组件都已上线");
            assertEquals(e.getValue(), keys(board), e.getKey());
            for (String key : e.getValue()) {
                if (key.equals("opportunity-stats") || key.equals("overdue-pi")) {
                    continue;
                }
                JsonNode data = ok(call(get(API + "/widgets/" + key), token));
                assertTrue(data.path("blocks").size() > 0, key + "：" + data);
            }
        }
    }

    private static Map<String, String> todoLinks(JsonNode widget) {
        Map<String, String> out = new java.util.HashMap<>();
        widget.path("blocks").get(0).path("todos").forEach(t -> out.put(t.path("title").asText(), t.path("link").asText()));
        return out;
    }

    /** UI-030：待办点进列表时带上与数字同口径的筛选 */
    @Test
    void todoLinks_carrySameFilterAsCount() throws Exception {
        int[] all = {DASHBOARD, PI, ORDERS, SHIPMENTS, 100051, 100079};
        role(ROLE, "sales");
        loginWithResources("it_wb_links", all);
        Map<String, String> sales = todoLinks(ok(call(get(API + "/widgets/my-todo"), token("it_wb_links", T))));
        assertEquals("/sales/pi?unpaid=1", sales.get("PI 待收款"));
        assertEquals("/sales/orders?progressCode=RECEIVED", sales.get("订单待出运"));

        jdbc.update("delete from role where code = ?", ROLE);
        role(ROLE, "boss");
        loginWithResources("it_wb_links", all);
        Map<String, String> boss = todoLinks(ok(call(get(API + "/widgets/risk-alerts"), token("it_wb_links", T))));
        assertEquals("/sales/orders?loss=1&dateFrom=" + java.time.LocalDate.now().withDayOfMonth(1) + "&dateTo=" + java.time.LocalDate.now(),
                boss.get("毛利为负的订单"));
        assertEquals("/sales/pi?expired=1", boss.get("过期未收款 PI"));
    }

    @Test
    void salesTodo_countsOwnInquiriesOnly() throws Exception {
        role(ROLE, "sales");
        loginWithResources("it_wb_sales", DASHBOARD, 100051);
        long me = 99000002L;
        jdbc.update("insert into customer_inquiry (tenant_id, inquiry_code, owner_id, status, inquiry_date, quote_deadline) values (?, 'IT-WB-1', ?, 3, curdate(), curdate()), "
                + "(?, 'IT-WB-2', ?, 6, curdate(), curdate()), (?, 'IT-WB-3', ?, 6, curdate(), curdate()), (?, 'IT-WB-4', 1, 6, curdate(), curdate())", T, me, T, me, T, me, T);
        try {
            JsonNode todos = ok(call(get(API + "/widgets/my-todo"), token("it_wb_sales", T))).path("blocks").get(0).path("todos");
            assertEquals(2, todos.size(), "只有询盘单权限：只给询盘的两项");
            assertEquals("待确认的询盘", todos.get(0).path("title").asText());
            assertEquals(1, todos.get(0).path("count").asInt());
            assertEquals(2, todos.get(1).path("count").asInt(), "别人的询盘不算（角色数据范围为本人）");
            // UI-030：点进询盘列表时按同一状态筛选，不限日期
            assertEquals("/inquiry/customer-inquiries?status=3", todos.get(0).path("link").asText());
            assertEquals("/inquiry/customer-inquiries?status=6", todos.get(1).path("link").asText());
        } finally {
            jdbc.update("delete from customer_inquiry where tenant_id = ? and inquiry_code like 'IT-WB-%'", T);
        }
    }

    /** spec system/workbench「业务员看板」「业务负责人看板」：售后要更新客户、售后逾期 */
    @Test
    void afterSalesTodos_mine_andTeamOverdue() throws Exception {
        long me = 99000002L;
        jdbc.update("insert into after_sales (tenant_id, as_no, customer_id, owner_id, level_code, status, due_at, due_type) values "
                + "(?, 'IT-AS-1', 1, ?, 'S1', 1, now() - interval 3 hour, 1), (?, 'IT-AS-2', 1, ?, 'S2', 1, now() - interval 1 hour, 2), "
                + "(?, 'IT-AS-3', 1, ?, 'S3', 1, now() + interval 30 minute, 2), (?, 'IT-AS-4', 1, ?, 'S3', 1, now() + interval 1 day, 2), "
                + "(?, 'IT-AS-5', 1, ?, 'S2', 3, null, 0), (?, 'IT-AS-6', 1, 1, 'S1', 1, now() - interval 9 hour, 1)",
                T, me, T, me, T, me, T, me, T, me, T);
        try {
            role(ROLE, "sales");
            loginWithResources("it_wb_as", DASHBOARD, 100120);
            JsonNode todos = ok(call(get(API + "/widgets/my-todo"), token("it_wb_as", T))).path("blocks").get(0).path("todos");
            assertEquals(1, todos.size(), "只有售后管理权限：只给售后一项");
            assertEquals("售后要更新客户", todos.get(0).path("title").asText());
            assertEquals(3, todos.get(0).path("count").asInt(), "本人的已逾期 2 张 + 快到期 1 张；别人的、已结案的不算");
            assertEquals("/sales/after-sales?overdue=soon", todos.get(0).path("link").asText());

            jdbc.update("delete from role where code = ?", ROLE);
            role(ROLE, "sales_lead");
            loginWithResources("it_wb_as", DASHBOARD, 100120);
            JsonNode lead = ok(call(get(API + "/widgets/follow-ups"), token("it_wb_as", T))).path("blocks").get(0).path("todos");
            assertEquals(1, lead.size());
            assertEquals("售后逾期", lead.get(0).path("title").asText());
            assertEquals(2, lead.get(0).path("count").asInt(), "数据范围为本人：别人的不算");
            assertTrue(lead.get(0).path("desc").asText().startsWith("最久的 IT-AS-1（S1 · "), lead.get(0).path("desc").asText());
            assertTrue(lead.get(0).path("desc").asText().endsWith("已逾期 3 小时"), lead.get(0).path("desc").asText());
        } finally {
            jdbc.update("delete from after_sales where tenant_id = ? and as_no like 'IT-AS-%'", T);
        }
    }

    @Test
    void roleManagement_choosesTemplate() throws Exception {
        loginAsAdmin("it_wb_admin");
        String admin = token("it_wb_admin", 0);
        JsonNode templates = ok(call(get(API + "/templates"), admin));
        assertEquals(7, templates.size());
        assertEquals("boss", templates.get(0).path("code").asText());

        ok(call(json(post("/api/v1/system/roles"), write(Map.of("name", "IT工作台华东销售组长", "workbenchTemplate", "sales_lead"))), admin));
        Map<String, Object> row = jdbc.queryForMap("select id, workbench_template from role where name = 'IT工作台华东销售组长'");
        assertEquals("sales_lead", row.get("workbench_template"));
        assertEquals("工作台模板不存在", fail(call(json(put("/api/v1/system/roles/" + row.get("id")),
                write(Map.of("name", "IT工作台华东销售组长", "workbenchTemplate", "nope"))), admin)).path("message").asText());
        ok(call(json(put("/api/v1/system/roles/" + row.get("id")), write(Map.of("name", "IT工作台华东销售组长", "workbenchTemplate", "sales"))), admin));
        assertEquals("sales", jdbc.queryForObject("select workbench_template from role where id = ?", String.class, row.get("id")));
        // 不传模板：新建为通用
        ok(call(json(post("/api/v1/system/roles"), write(Map.of("name", "IT工作台默认"))), admin));
        assertEquals("general", jdbc.queryForObject("select workbench_template from role where name = 'IT工作台默认'", String.class));
    }
}
