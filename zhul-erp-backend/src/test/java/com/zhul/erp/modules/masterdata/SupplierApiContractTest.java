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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 供应商基础信息接口契约（enrich-supplier-basic-info、enrich-supplier-settlement-attachments）：走完整的 HTTP + JWT + 权限链路。
 */
@AutoConfigureMockMvc
class SupplierApiContractTest extends IntegrationTestBase {

    private static final String BASE = "/api/v1/masterdata/suppliers";
    private static final int TENANT = 1;
    private static final int RES_EDIT = 110152;
    private static final int RES_EXPORT = 110155;
    private static final int RES_ADD = 110151;
    /** 供应商管理菜单：能访问它才能下载附件 */
    private static final int MENU_SUPPLIER = 100062;
    private static final byte[] PDF = "%PDF-1.4 test".getBytes(StandardCharsets.US_ASCII);

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
        jdbc.update("delete from supplier_bank_account where tenant_id = ?", TENANT);
        jdbc.update("delete from supplier_attachment where tenant_id = ?", TENANT);
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
                 "region":"上海市/上海市/浦东新区","wechat":"zhangsan_sh",
                 "accounts":[
                   {"accountType":1,"accountName":"上海电子科技有限公司","bankName":"中国工商银行上海张江支行",
                    "accountNo":"6222021234560008888","defaultAccount":true},
                   {"accountType":2,"accountName":"张三","bankName":"招商银行上海分行","accountNo":"6212261001011234",
                    "payeePhone":"13812345678","payeeIdNo":"31010119900101123x"}]}
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
            "{\"name\":\"A\",\"accounts\":[{\"accountType\":1,\"accountName\":\"A\",\"bankName\":\"B\",\"accountNo\":\"6222-0212\"}]}",
            "{\"name\":\"A\",\"accounts\":[{\"accountType\":2,\"accountName\":\"A\",\"bankName\":\"B\",\"accountNo\":\"62220212\",\"payeeIdNo\":\"123\"}]}",
            "{\"name\":\"A\",\"wechat\":\"" + "w".repeat(65) + "\"}",
            "{\"name\":\"A\",\"establishedDate\":\"" + tomorrow + "\"}",
            "{\"name\":\"A\",\"registeredCapital\":-1}",
            "{\"name\":\"A\",\"supplierType\":9}"
        }) {
            assertEquals(400, perform(json(post(BASE), body), admin).getStatus(), body);
        }
    }

    @Test
    void create_ignoresRequestedCode_andRejectsDuplicateCreditCode() throws Exception {
        loginAsAdmin("supplier_admin");
        String admin = token("supplier_admin");
        createFull(admin);

        JsonNode created = call(json(post(BASE),
                "{\"supplierCode\":\"ABC001\",\"name\":\"B\",\"supplierType\":1,\"force\":true}"), admin);
        JsonNode supplier = created.path("data").path("createdSupplier");
        assertEquals(String.format("SUP%05d", supplier.path("id").asLong()), supplier.path("supplierCode").asText());

        JsonNode dupCredit = call(json(post(BASE),
                "{\"name\":\"C\",\"supplierType\":1,\"force\":true,"
                        + "\"creditCode\":\"91310115ma1g832x01\"}"), admin);
        assertEquals("SUPPLIER_CREDIT_CODE_DUPLICATE", dupCredit.path("data").path("errorCode").asText());
        assertTrue(dupCredit.path("message").asText().contains("上海电子科技有限公司"));
    }

    /** spec master-data/supplier「供应商默认付款条件」 */
    @Test
    void defaultPaymentTerms_validatedAndShown() throws Exception {
        loginAsAdmin("supplier_admin");
        String admin = token("supplier_admin");
        JsonNode bad = call(json(post(BASE), "{\"name\":\"付款条件A\",\"supplierType\":1,\"force\":true,"
                + "\"paymentTerms\":[{\"percent\":30,\"trigger\":1},{\"percent\":60,\"trigger\":3}]}"), admin);
        assertEquals("各期比例合计须为 100%", bad.path("message").asText());
        JsonNode created = call(json(post(BASE), "{\"name\":\"付款条件A\",\"supplierType\":1,\"force\":true,"
                + "\"paymentTerms\":[{\"percent\":100,\"trigger\":3,\"days\":30}]}"), admin);
        long id = created.path("data").path("createdSupplier").path("id").asLong();
        JsonNode detail = call(get(BASE + "/" + id), admin).path("data");
        assertEquals("入库后 30 天", detail.path("paymentTermsText").asText());
        assertEquals(30, detail.path("paymentTerms").get(0).path("days").asInt());
        JsonNode plain = call(json(post(BASE), "{\"name\":\"付款条件B\",\"supplierType\":1,\"force\":true}"), admin);
        long other = plain.path("data").path("createdSupplier").path("id").asLong();
        assertEquals(0, call(get(BASE + "/" + other), admin).path("data").path("paymentTerms").size(), "未设置时为空");
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
    void detailMasksAccounts_formRequiresEditPermission_idNoAlwaysMaskedAndEncrypted() throws Exception {
        loginAsAdmin("supplier_admin");
        long id = createFull(token("supplier_admin"));

        loginWithResources("supplier_viewer", RES_EXPORT);
        String viewer = token("supplier_viewer");
        JsonNode detail = call(get(BASE + "/" + id), viewer).path("data");
        assertEquals("zhangsan_sh", detail.path("wechat").asText());
        JsonNode corporate = detail.path("accounts").get(0);
        JsonNode personal = detail.path("accounts").get(1);
        assertEquals("6222 **** **** 8888", corporate.path("accountNo").asText());
        assertTrue(corporate.path("defaultAccount").asBoolean());
        assertEquals("138****5678", personal.path("payeePhone").asText());
        assertEquals("310101********123X", personal.path("payeeIdNoMasked").asText());
        assertFalse(personal.has("payeeIdNo"));
        assertEquals(403, perform(get(BASE + "/" + id + "/form"), viewer).getStatus());

        loginWithResources("supplier_editor", RES_EDIT);
        String editor = token("supplier_editor");
        JsonNode form = call(get(BASE + "/" + id + "/form"), editor).path("data");
        assertEquals("6222021234560008888", form.path("accounts").get(0).path("accountNo").asText());
        assertEquals("310101********123X", form.path("accounts").get(1).path("payeeIdNoMasked").asText());

        String cipher = jdbc.queryForObject("select payee_id_no from supplier_bank_account where supplier_id = ? and account_type = 2",
                String.class, id);
        assertFalse(cipher.contains("31010119900101123"), "身份证号不应明文存储");

        // 编辑只改开户银行、不重新填身份证号：身份证号保持原值
        long personalId = form.path("accounts").get(1).path("id").asLong();
        long corporateId = form.path("accounts").get(0).path("id").asLong();
        JsonNode res = call(json(put(BASE + "/" + id), """
                {"name":"上海电子科技有限公司","supplierType":1,"status":1,
                 "accounts":[
                   {"id":%d,"accountType":1,"accountName":"上海电子科技有限公司","bankName":"中国工商银行上海张江支行",
                    "accountNo":"6222021234560008888","defaultAccount":true},
                   {"id":%d,"accountType":2,"accountName":"张三","bankName":"招商银行上海浦东支行",
                    "accountNo":"6212261001011234","payeePhone":"13812345678"}]}
                """.formatted(corporateId, personalId)), editor);
        assertEquals(0, res.path("code").asInt(), res.toString());
        assertEquals(cipher, jdbc.queryForObject("select payee_id_no from supplier_bank_account where id = ?", String.class, personalId));
    }

    @Test
    void attachments_uploadSaveAndDownloadWithMenuPermission() throws Exception {
        loginAsAdmin("supplier_admin");
        String admin = token("supplier_admin");
        long id = createFull(admin);

        JsonNode tiff = call(multipart(BASE + "/attachments").file(new MockMultipartFile("file", "合同.tiff", "image/tiff",
                new byte[]{'I', 'I', 42, 0, 1, 2, 3, 4})), admin);
        assertEquals("只支持 PDF、JPG、PNG", tiff.path("message").asText());

        JsonNode up = call(multipart(BASE + "/attachments").file(new MockMultipartFile("file", "营业执照.pdf",
                "application/pdf", PDF)), admin).path("data");
        String fileKey = up.path("fileKey").asText();
        assertTrue(fileKey.startsWith("supplier/" + TENANT + "/"), up.toString());
        assertEquals(0, jdbc.queryForObject("select count(*) from supplier_attachment where supplier_id = ?", Integer.class, id),
                "上传后未保存不应出现在附件中");

        JsonNode forged = call(json(put(BASE + "/" + id), "{\"name\":\"上海电子科技有限公司\",\"supplierType\":1,\"status\":1,"
                + "\"attachments\":[{\"category\":1,\"fileKey\":\"supplier/9/202609/" + "a".repeat(32) + ".pdf\"}]}"), admin);
        assertEquals("附件不存在，请重新上传", forged.path("message").asText());

        JsonNode saved = call(json(put(BASE + "/" + id), "{\"name\":\"上海电子科技有限公司\",\"supplierType\":1,\"status\":1,"
                + "\"attachments\":[{\"category\":1,\"fileName\":\"营业执照.pdf\",\"fileKey\":\"" + fileKey + "\"}]}"), admin);
        assertEquals(0, saved.path("code").asInt(), saved.toString());
        JsonNode att = call(get(BASE + "/" + id), admin).path("data").path("attachments").get(0);
        assertEquals("营业执照.pdf", att.path("fileName").asText());
        assertEquals(PDF.length, att.path("fileSize").asInt());
        assertFalse(att.has("fileKey"));
        String url = BASE + "/" + id + "/attachments/" + att.path("id").asLong();

        loginWithResources("supplier_viewer", MENU_SUPPLIER);
        MockHttpServletResponse download = perform(get(url), token("supplier_viewer"));
        assertEquals(200, download.getStatus());
        assertEquals("application/pdf", download.getContentType());
        assertTrue(download.getHeader("Content-Disposition").startsWith("attachment; filename*=UTF-8''"));
        assertEquals(new String(PDF, StandardCharsets.US_ASCII), download.getContentAsString(StandardCharsets.US_ASCII));

        loginWithResources("supplier_nomenu", RES_ADD);
        assertEquals(403, perform(get(url), token("supplier_nomenu")).getStatus());
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
