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
    }

    @AfterEach
    void cleanup() {
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

        // 老板看板的组件还没上线：先显示通用看板的组件，并标出兜底
        role(ROLE, "boss");
        JsonNode boss = ok(call(get(API), token)).path("boards").get(0);
        assertEquals("boss", boss.path("code").asText());
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
