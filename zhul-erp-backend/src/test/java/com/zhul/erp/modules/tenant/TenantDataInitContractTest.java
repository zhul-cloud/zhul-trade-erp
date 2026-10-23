package com.zhul.erp.modules.tenant;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.tenant.dto.SaveTenantRequest;
import com.zhul.erp.modules.tenant.service.TenantService;
import com.zhul.erp.support.TenantContractSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * spec system/tenant-data-init：平台为新租户写入福唯模板的部门、岗位、角色、初始汇率，租户管理员归入总经办；
 * 重复执行只补缺（已调整的角色权限不变、删除的岗位重建）；自建部门保留；租户账号不能执行；租户不存在报错。
 */
class TenantDataInitContractTest extends TenantContractSupport {

    private static final int TEST_PACKAGE_ID = 99000401;
    private static final String PLATFORM_USER = "it_init_platform";

    @Autowired
    private TenantService tenantService;

    private int tenantId;
    private boolean insertedPlatformRate;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(0);
        jdbc.update("insert into tenant_package (id, name, menu_ids, remark, status) values (?, '初始化测试套餐', '[]', '', 1)",
                TEST_PACKAGE_ID);
        SaveTenantRequest req = new SaveTenantRequest();
        req.setName("初始化测试_" + System.nanoTime());
        req.setPackageId(TEST_PACKAGE_ID);
        req.setContactName("测试联系人");
        req.setContactPhone("13800004444");
        req.setContactEmail("it-init-" + System.nanoTime() + "@example.com");
        req.setExpireDate(LocalDate.now().plusYears(1));
        tenantId = tenantService.create(req).getTenant().getId();
        TenantContext.clear();
        insertedPlatformRate = jdbc.update("insert into exchange_rate (tenant_id, currency_code, rate, source, updated_by_id, rate_time, status) "
                + "select 0, 'USD', 6.600000, 1, 0, now(), 1 from dual "
                + "where not exists (select 1 from exchange_rate where tenant_id = 0 and currency_code = 'USD')") > 0;
        loginAsAdmin(PLATFORM_USER);
    }

    @AfterEach
    void cleanup() {
        for (String sql : new String[]{
                "delete from role_resource where role_code in (select code from role where tenant_id = ?)",
                "delete from role_org where role_code in (select code from role where tenant_id = ?)",
                "delete from role where tenant_id = ?",
                "delete from position where tenant_id = ?",
                "delete from department where tenant_id = ?",
                "delete from exchange_rate_log where tenant_id = ?",
                "delete from exchange_rate where tenant_id = ?",
                "delete from account_local_auth where account_id in (select id from account where tenant_id = ?)",
                "delete from account where tenant_id = ?",
                "delete from user_basic where tenant_id = ?",
                "delete from tenant where id = ?"}) {
            jdbc.update(sql, tenantId);
        }
        jdbc.update("delete from tenant_package where id = ?", TEST_PACKAGE_ID);
        if (insertedPlatformRate) {
            jdbc.update("delete from exchange_rate where tenant_id = 0 and currency_code = 'USD'");
        }
    }

    private JsonNode init(int id, String token) throws Exception {
        return call(post("/api/v1/tenant/tenants/" + id + "/init-data"), token);
    }

    private static Map<String, int[]> counts(JsonNode data) {
        Map<String, int[]> map = new HashMap<>();
        data.path("items").forEach(i -> map.put(i.path("type").asText(),
                new int[]{i.path("created").asInt(), i.path("existed").asInt()}));
        return map;
    }

    private int dept(String name) {
        return jdbc.queryForObject("select id from department where tenant_id = ? and name = ? and deleted_at is null",
                Integer.class, tenantId, name);
    }

    @Test
    void initializesNewTenant() throws Exception {
        JsonNode data = ok(init(tenantId, token(PLATFORM_USER, 0)));
        Map<String, int[]> c = counts(data);
        assertEquals(13, c.get("部门")[0]);
        assertEquals(18, c.get("岗位")[0]);
        assertEquals(9, c.get("角色")[0]);
        assertEquals(1, c.get("汇率")[0], "按平台汇率写入 USD");
        assertEquals("INDUSTRIAL_CONTROL", data.path("industryCode").asText());
        assertTrue(data.path("todos").toString().contains("收款账户"));

        String month = YearMonth.now().toString();
        Map<String, Object> ali = jdbc.queryForMap(
                "select code, is_business_unit, date_format(bu_start_month, '%Y-%m') m from department where tenant_id = ? "
                        + "and name = '阿里国际站事业部'", tenantId);
        assertEquals(1, ((Number) ali.get("is_business_unit")).intValue());
        assertEquals(month, ali.get("m"), "事业部开始核算月份为初始化当月");
        assertEquals(ali.get("code"), jdbc.queryForObject("select o.org_code from role_org o join role r on r.code = o.role_code "
                + "where r.tenant_id = ? and r.name = '阿里国际站事业部合伙人'", String.class, tenantId), "合伙人只看本事业部");
        assertEquals(2, jdbc.queryForObject("select permission_scope from role where tenant_id = ? and name = '阿里国际站事业部合伙人'",
                Integer.class, tenantId));
        assertTrue(jdbc.queryForObject("select count(*) from role_resource rr join role r on r.code = rr.role_code "
                + "where r.tenant_id = ? and r.name = '业务员'", Integer.class, tenantId) > 0, "角色带菜单权限");
        assertEquals(dept("总经办"), jdbc.queryForObject("select dept_id from user_basic where tenant_id = ? and role_code = 'ROLE_ADMIN'",
                Integer.class, tenantId), "租户管理员归入总经办");
        assertEquals(0, new BigDecimal("6.600000").compareTo(jdbc.queryForObject(
                "select rate from exchange_rate where tenant_id = ? and currency_code = 'USD'", BigDecimal.class, tenantId)));
        assertEquals(1, jdbc.queryForObject("select count(*) from exchange_rate_log where tenant_id = ?", Integer.class, tenantId),
                "汇率留变更记录");
    }

    @Test
    void rerunOnlyFillsMissing() throws Exception {
        String token = token(PLATFORM_USER, 0);
        ok(init(tenantId, token));
        String salesRole = jdbc.queryForObject("select code from role where tenant_id = ? and name = '业务员'", String.class, tenantId);
        jdbc.update("delete from role_resource where role_code = ? and resource_id <> 100001", salesRole);
        jdbc.update("update position set deleted_at = now() where tenant_id = ? and code = 'GW0017'", tenantId);
        int deptCount = jdbc.queryForObject("select count(*) from department where tenant_id = ?", Integer.class, tenantId);

        Map<String, int[]> c = counts(ok(init(tenantId, token)));
        assertEquals(0, c.get("部门")[0]);
        assertEquals(13, c.get("部门")[1]);
        assertEquals(1, c.get("岗位")[0], "删除的岗位重新新建");
        assertEquals(17, c.get("岗位")[1]);
        assertEquals(0, c.get("角色")[0]);
        assertEquals(0, c.get("汇率")[0]);
        assertEquals(deptCount, jdbc.queryForObject("select count(*) from department where tenant_id = ?", Integer.class, tenantId));
        assertEquals(1, jdbc.queryForObject("select count(*) from role_resource where role_code = ?", Integer.class, salesRole),
                "已调整的角色权限不覆盖");
    }

    @Test
    void keepsOwnDepartments() throws Exception {
        jdbc.update("insert into department (tenant_id, pid, code, name, level, status) values (?, 0, '', '我的公司', 1, 1)", tenantId);
        int root = jdbc.queryForObject("select id from department where tenant_id = ? and name = '我的公司'", Integer.class, tenantId);
        jdbc.update("insert into department (tenant_id, pid, code, name, level, status) values (?, ?, '', '销售部', 2, 1)", tenantId, root);

        Map<String, int[]> c = counts(ok(init(tenantId, token(PLATFORM_USER, 0))));
        assertEquals(12, c.get("部门")[0], "唯一的顶级部门当作公司根节点");
        assertEquals(1, c.get("部门")[1]);
        assertEquals(1, jdbc.queryForObject("select count(*) from department where tenant_id = ? and name = '销售部' and deleted_at is null",
                Integer.class, tenantId), "自建部门保留");
        assertEquals(root, jdbc.queryForObject("select pid from department where tenant_id = ? and name = '总经办'", Integer.class, tenantId));
    }

    @Test
    void tenantAccountCannotInitialize() throws Exception {
        fail(init(tenantId, token(PLATFORM_USER, tenantId)));
        assertEquals(0, jdbc.queryForObject("select count(*) from department where tenant_id = ?", Integer.class, tenantId),
                "没有写入任何数据");
    }

    @Test
    void unknownTenant() throws Exception {
        JsonNode res = fail(init(99999991, token(PLATFORM_USER, 0)));
        assertEquals("租户不存在", res.path("message").asText());
    }
}
