package com.zhul.erp.modules.masterdata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.constants.RedisKeyConstants;
import com.zhul.erp.common.utils.JwtUtils;
import com.zhul.erp.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 供应商基础信息接口契约（enrich-supplier-basic-info）：走完整的 HTTP + JWT + 权限链路。
 */
@AutoConfigureMockMvc
class SupplierApiContractTest extends IntegrationTestBase {

    private static final String BASE = "/api/v1/masterdata/suppliers";
    private static final int TENANT = 1;
    private static final int RES_EDIT = 110152;
    private static final int RES_EXPORT = 110155;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtUtils jwtUtils;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanData() {
        jdbc.update("delete from supplier where tenant_id = ?", TENANT);
    }

    private String token(String username) {
        String token = jwtUtils.generateToken(Map.of("tenantId", TENANT), username, 3600);
        redis.opsForValue().set(RedisKeyConstants.TOKEN_PREFIX + token, "1");
        return token;
    }

    private MockHttpServletResponse perform(MockHttpServletRequestBuilder request, String token) throws Exception {
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(request).andReturn().getResponse();
    }

    private JsonNode call(MockHttpServletRequestBuilder request, String token) throws Exception {
        return objectMapper.readTree(perform(request, token).getContentAsString(StandardCharsets.UTF_8));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private long createFull(String token) throws Exception {
        JsonNode res = call(json(post(BASE), """
                {"supplierCode":"SUP00001","name":"上海电子科技有限公司","supplierType":1,"status":1,"force":true,
                 "creditCode":"91310115MA1G832X01","registeredCapital":5000.00,"establishedDate":"2018-05-15",
                 "region":"上海市/上海市/浦东新区","bankName":"中国工商银行上海张江支行",
                 "bankAccount":"6222021234560008888"}
                """), token);
        assertEquals(0, res.path("code").asInt(), res.toString());
        return res.path("data").path("createdSupplier").path("id").asLong();
    }

    @Test
    void create_withInvalidFields_returns400() throws Exception {
        loginAsAdmin("supplier_admin");
        String admin = token("supplier_admin");

        String tomorrow = LocalDate.now().plusDays(1).toString();
        for (String body : new String[]{
            "{\"name\":\"A\",\"creditCode\":\"123\"}",
            "{\"name\":\"A\",\"bankAccount\":\"6222-0212\"}",
            "{\"name\":\"A\",\"establishedDate\":\"" + tomorrow + "\"}",
            "{\"name\":\"A\",\"supplierCode\":\"SUP-001\"}",
            "{\"name\":\"A\",\"registeredCapital\":-1}",
            "{\"name\":\"A\",\"supplierType\":9}"
        }) {
            assertEquals(400, perform(json(post(BASE), body), admin).getStatus(), body);
        }
    }

    @Test
    void create_duplicateCodeAndCreditCode_returnErrorCodes() throws Exception {
        loginAsAdmin("supplier_admin");
        String admin = token("supplier_admin");
        createFull(admin);

        JsonNode dupCode = call(json(post(BASE),
                "{\"supplierCode\":\"sup00001\",\"name\":\"B\",\"supplierType\":1,\"force\":true}"), admin);
        assertEquals("SUPPLIER_CODE_DUPLICATE", dupCode.path("data").path("errorCode").asText(), dupCode.toString());

        JsonNode dupCredit = call(json(post(BASE),
                "{\"supplierCode\":\"SUP2\",\"name\":\"B\",\"supplierType\":1,\"force\":true,"
                        + "\"creditCode\":\"91310115ma1g832x01\"}"), admin);
        assertEquals("SUPPLIER_CREDIT_CODE_DUPLICATE", dupCredit.path("data").path("errorCode").asText());
        assertTrue(dupCredit.path("message").asText().contains("上海电子科技有限公司"));
    }

    @Test
    void inlineCreateWithoutCode_getsGeneratedCode() throws Exception {
        loginAsAdmin("supplier_admin");
        String admin = token("supplier_admin");

        JsonNode res = call(json(post(BASE), "{\"name\":\"询盘内联创建\"}"), admin);
        long id = res.path("data").path("createdSupplier").path("id").asLong();

        assertEquals(String.format("SUP%05d", id), res.path("data").path("createdSupplier").path("supplierCode").asText());
    }

    @Test
    void detailMasksBankAccount_formRequiresEditPermission() throws Exception {
        loginAsAdmin("supplier_admin");
        long id = createFull(token("supplier_admin"));

        loginWithResources("supplier_viewer", RES_EXPORT);
        String viewer = token("supplier_viewer");
        JsonNode detail = call(get(BASE + "/" + id), viewer);
        assertEquals("6222 **** **** 8888", detail.path("data").path("bankAccount").asText());
        assertEquals(403, perform(get(BASE + "/" + id + "/form"), viewer).getStatus());

        loginWithResources("supplier_editor", RES_EDIT);
        JsonNode form = call(get(BASE + "/" + id + "/form"), token("supplier_editor"));
        assertEquals("6222021234560008888", form.path("data").path("bankAccount").asText());
    }

    @Test
    void export_requiresPermissionAndReturnsXlsx() throws Exception {
        loginAsAdmin("supplier_admin");
        createFull(token("supplier_admin"));

        loginWithResources("supplier_editor", RES_EDIT);
        assertEquals(403, perform(get(BASE + "/export"), token("supplier_editor")).getStatus());

        loginWithResources("supplier_exporter", RES_EXPORT);
        MockHttpServletResponse res = perform(get(BASE + "/export").param("status", "1"), token("supplier_exporter"));
        assertEquals(200, res.getStatus());
        assertTrue(res.getContentType().startsWith("application/vnd.openxmlformats"));
        assertTrue(res.getContentAsByteArray().length > 0);
    }

    @Test
    void regions_returnTreeForLoggedInUser() throws Exception {
        loginAsAdmin("supplier_admin");
        JsonNode regions = call(get("/api/v1/masterdata/regions"), token("supplier_admin"));
        assertEquals(31, regions.path("data").size());
    }

    @Test
    void regions_rejectUnauthenticated() throws Exception {
        assertEquals(401, call(get("/api/v1/masterdata/regions"), null).path("code").asInt());
    }
}
