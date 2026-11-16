package com.zhul.erp.modules.logistics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.constants.RedisKeyConstants;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.utils.JwtUtils;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.logistics.support.LogisticsSupport;
import com.zhul.erp.modules.masterdata.customerprofile.service.CustomerMergeService;
import com.zhul.erp.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 货代独立管理与客户指定货代（spec logistics/forwarder，openspec feedback-1116-batch2）：
 * 货代管理增改查、供应商管理不含货代、按客户取货代的范围与顺序、归属校验、客户合并时转移归属。
 */
@AutoConfigureMockMvc
class ForwarderContractTest extends IntegrationTestBase {

    private static final String FW = "/api/v1/logistics/forwarders";
    private static final String SUP = "/api/v1/masterdata/suppliers";

    @Autowired private MockMvc mvc;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private StringRedisTemplate redis;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private LogisticsSupport support;
    @Autowired private CustomerMergeService mergeService;

    private String admin;

    @BeforeEach
    void setUp() {
        cleanup();
        loginAsAdmin("it_fw_admin");
        admin = jwtUtils.generateToken(Map.of("tenantId", 0), "it_fw_admin", 3600);
        redis.opsForValue().set(RedisKeyConstants.TOKEN_PREFIX + admin, "1");
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private void cleanup() {
        jdbc.update("delete from supplier_bank_account where supplier_id in (select id from supplier where tenant_id = 0 and name like 'ITFW%')");
        jdbc.update("delete from supplier where tenant_id = 0 and name like 'ITFW%'");
        jdbc.update("delete from customer_contact where tenant_id = 0");
        jdbc.update("delete from customer where tenant_id = 0 and name like 'ITFW%'");
    }

    private JsonNode call(MockHttpServletRequestBuilder r) throws Exception {
        r.header("Authorization", "Bearer " + admin);
        return objectMapper.readTree(mvc.perform(r).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private JsonNode ok(JsonNode res) {
        assertEquals(0, res.path("code").asInt(), res.toString());
        return res.path("data");
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, Object body) throws Exception {
        return b.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
    }

    private long customer(String name) {
        jdbc.update("insert into customer (tenant_id, customer_code, name, name_key, name_cn, country, owner_id, status) "
                + "values (0, ?, ?, ?, '', 'Germany', 99000002, 1)", "ITFW" + System.nanoTime() % 1000000, name,
                name.toLowerCase().replace(" ", ""));
        return jdbc.queryForObject("select max(id) from customer where tenant_id = 0", Long.class);
    }

    private long forwarder(String name, Long customerId) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("volumeDivisor", 6000);
        body.put("contactName", "陈经理");
        body.put("customerId", customerId);
        return ok(call(json(post(FW), body))).asLong();
    }

    private static List<Long> ids(JsonNode list) {
        List<Long> out = new ArrayList<>();
        list.forEach(r -> out.add(r.path("id").asLong()));
        return out;
    }

    @Test
    void manage_ownerScope_supplierExcludes_mergeMovesOwnership() throws Exception {
        long acme = customer("ITFW Acme");
        long siam = customer("ITFW Siam");
        JsonNode before = ok(call(get(FW + "/counts")));
        long company = forwarder("ITFW 深圳捷运", null);
        long acmeFw = forwarder("ITFW Acro Freight", acme);
        long siamFw = forwarder("ITFW Siam Cargo", siam);

        // 货代管理：归属筛选、数量、行内容
        JsonNode counts = ok(call(get(FW + "/counts")));
        assertEquals(before.path("company").asLong() + 1, counts.path("company").asLong());
        assertEquals(before.path("customer").asLong() + 2, counts.path("customer").asLong());
        JsonNode owned = ok(call(get(FW + "/page").param("owner", "customer").param("keyword", "ITFW"))).path("records");
        assertEquals(List.of(acmeFw, siamFw).stream().sorted().toList(), ids(owned).stream().sorted().toList());
        JsonNode acmeRow = ok(call(get(FW + "/page").param("customerId", String.valueOf(acme)))).path("records").get(0);
        assertEquals("ITFW Acme", acmeRow.path("customerName").asText());
        assertEquals(6000, acmeRow.path("volumeDivisor").asInt());

        // 供应商管理不再列出、也不能新增或修改货代
        assertEquals(0, ok(call(get(SUP + "/page").param("name", "ITFW"))).path("total").asInt());
        assertEquals(0, ok(call(get(SUP).param("keyword", "ITFW"))).size());
        JsonNode rejected = call(json(post(SUP), Map.of("name", "ITFW 新货代", "supplierType", 3)));
        assertTrue(rejected.path("message").asText().contains("货代管理"), rejected.toString());
        JsonNode rejectedUpdate = call(json(put(SUP + "/" + company), Map.of("name", "ITFW 深圳捷运", "supplierType", 1, "status", 1)));
        assertTrue(rejectedUpdate.path("message").asText().contains("货代管理"), rejectedUpdate.toString());

        // 修改：改归属客户、改体积系数
        ok(call(json(put(FW + "/" + company), Map.of("name", "ITFW 深圳捷运", "volumeDivisor", 5000))));
        assertEquals(5000, jdbc.queryForObject("select volume_divisor from supplier where id = ?", Integer.class, company));

        // 按客户取货代：公司货代 + 这个客户的，客户指定的在前，看不到其他客户的
        JsonNode forAcme = ok(call(get(FW).param("customerId", String.valueOf(acme))));
        List<Long> forAcmeIds = ids(forAcme);
        assertEquals(acmeFw, forAcmeIds.get(0));
        assertTrue(forAcme.get(0).path("customerOwned").asBoolean());
        assertTrue(forAcmeIds.contains(company));
        assertFalse(forAcmeIds.contains(siamFw));
        TenantContext.setTenantId(0);
        BizException e = assertThrows(BizException.class, () -> support.requireForwarder(siamFw, acme));
        assertTrue(e.getMessage().contains("其他客户指定"));
        support.requireForwarder(acmeFw, acme);
        support.requireForwarder(company, acme);

        // 客户详情的「客户指定货代」
        assertEquals(List.of(acmeFw), ids(ok(call(get(FW + "/by-customer/" + acme)))));

        // 停用后不再出现在可选列表
        ok(call(json(put(FW + "/" + acmeFw + "/status"), Map.of("status", 0))));
        assertFalse(ids(ok(call(get(FW).param("customerId", String.valueOf(acme))))).contains(acmeFw));

        // 合并客户：被合并客户的指定货代归到保留客户
        // 接口请求结束时会清掉当前线程的租户上下文与登录态，直接调服务前重新设置
        loginAsAdmin("it_fw_admin");
        TenantContext.setTenantId(0);
        mergeService.merge(acme, siam);
        assertEquals(acme, jdbc.queryForObject("select customer_id from supplier where id = ?", Long.class, siamFw));
    }

    /** audit BUS-085：只有费用管理菜单的财务也能取货代列表（费用单收款方选「货代」） */
    @Test
    void expensesMenuCanListForwarders() throws Exception {
        long fw = forwarder("ITFW 费用货代", null);
        int menu = jdbc.queryForObject("select id from resource where path = '/finance/expenses' and type = 2 limit 1", Integer.class);
        loginWithResources("it_fw_fin", menu);
        String fin = jwtUtils.generateToken(Map.of("tenantId", 0), "it_fw_fin", 3600);
        redis.opsForValue().set(RedisKeyConstants.TOKEN_PREFIX + fin, "1");
        var res = mvc.perform(get(FW).header("Authorization", "Bearer " + fin)).andReturn().getResponse();
        assertEquals(200, res.getStatus(), "费用管理菜单能取货代列表");
        assertTrue(ids(ok(objectMapper.readTree(res.getContentAsString(StandardCharsets.UTF_8)))).contains(fw));
    }
}
