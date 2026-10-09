package com.zhul.erp.modules.document;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.support.TenantContractSupport;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** 单据模版（spec document/document-template）：两个测试租户共用平台内置 V1，各自上传的版本互不可见 */
class DocumentTemplateContractTest extends TenantContractSupport {

    private static final String API = "/api/v1/system/document-templates";
    private static final int TENANT_A = 99101;
    private static final int TENANT_B = 99102;
    private static final long VIEWER = 99000041L;
    private static final String VIEWER_ROLE = "IT_DOCV";
    private static final int MENU_DOC_TEMPLATE = 100076;
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @BeforeEach
    void setUp() {
        cleanup();
        loginAsAdmin("it_doc_admin");
        jdbc.update("insert into role_resource (role_code, resource_id, resource_code) values (?, ?, '')", VIEWER_ROLE, MENU_DOC_TEMPLATE);
        jdbc.update("insert into user_basic (id, tenant_id, name, username, role_code, status) values (?, 0, '只读', 'it_doc_viewer', ?, 1)",
                VIEWER, VIEWER_ROLE);
        jdbc.update("insert into account (id, tenant_id, user_id, username, admin_flag) values (?, 0, ?, 'it_doc_viewer', 0)", VIEWER, VIEWER);
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private void cleanup() {
        jdbc.update("delete from document_template_version where tenant_id in (?, ?)", TENANT_A, TENANT_B);
        jdbc.update("delete from document_template where tenant_id in (?, ?)", TENANT_A, TENANT_B);
        jdbc.update("delete from role_resource where role_code = ?", VIEWER_ROLE);
        jdbc.update("delete from account where id = ?", VIEWER);
        jdbc.update("delete from user_basic where id = ?", VIEWER);
    }

    // ---------------------------------------------------------------- 工具

    private JsonNode upload(String token, int docType, String fileName, byte[] bytes, String note) throws Exception {
        return call(multipart(API + "/" + docType + "/versions")
                .file(new MockMultipartFile("file", fileName, XLSX, bytes)).param("note", note), token);
    }

    private JsonNode versions(String token, int docType) throws Exception {
        return ok(call(get(API + "/" + docType + "/versions"), token));
    }

    private static JsonNode byNo(JsonNode versions, int no) {
        for (JsonNode v : versions) {
            if (v.path("versionNo").asInt() == no) {
                return v;
            }
        }
        throw new AssertionError("没有 V" + no + "：" + versions);
    }

    private static byte[] builtinQuotation() throws IOException {
        try (InputStream in = new ClassPathResource("document-template/quotation-v1.xlsx").getInputStream()) {
            return in.readAllBytes();
        }
    }

    private static byte[] xlsxWith(String cellText) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            wb.createSheet().createRow(2).createCell(1).setCellValue(cellText);
            wb.write(out);
            return out.toByteArray();
        }
    }

    // ---------------------------------------------------------------- 场景

    @Test
    void newTenantSeesFiveBuiltinDefaults() throws Exception {
        String a = token("it_doc_admin", TENANT_A);
        JsonNode types = ok(call(get(API), a));
        assertEquals(5, types.size());
        for (JsonNode t : types) {
            assertEquals(1, t.path("defaultVersionNo").asInt(), t.toString());
        }
        JsonNode v1 = versions(a, 1).get(0);
        assertTrue(v1.path("builtin").asBoolean());
        assertTrue(v1.path("isDefault").asBoolean());
        assertEquals("系统", v1.path("uploadedByName").asText());
    }

    @Test
    void uploadNewVersion_switchDefault_andTenantIsolation() throws Exception {
        String a = token("it_doc_admin", TENANT_A);
        String b = token("it_doc_admin", TENANT_B);
        JsonNode v2 = ok(upload(a, 1, "福唯报价单.xlsx", builtinQuotation(), "加公司抬头"));
        assertEquals(2, v2.path("versionNo").asInt());
        assertFalse(v2.path("isDefault").asBoolean(), "新版本上传后不自动成为默认");
        assertEquals(0, v2.path("warnings").size(), "示例模版有页码，不提示");
        long v2Id = v2.path("id").asLong();

        // 切换默认版本，记操作日志
        ok(call(put(API + "/versions/" + v2Id + "/default"), a));
        assertTrue(byNo(versions(a, 1), 2).path("isDefault").asBoolean());
        assertFalse(byNo(versions(a, 1), 1).path("isDefault").asBoolean());

        // 停用默认版本被拒；停用内置 V1 只影响本租户
        assertTrue(fail(call(put(API + "/versions/" + v2Id + "/enabled").param("enabled", "false"), a))
                .path("message").asText().contains("默认版本"));
        long v1Id = byNo(versions(a, 1), 1).path("id").asLong();
        ok(call(put(API + "/versions/" + v1Id + "/enabled").param("enabled", "false"), a));
        assertFalse(byNo(versions(a, 1), 1).path("enabled").asBoolean());

        // 租户 B：看不到 A 的 V2，内置 V1 仍启用且为默认，也不能操作 A 的版本
        JsonNode bVersions = versions(b, 1);
        assertEquals(1, bVersions.size());
        assertTrue(bVersions.get(0).path("enabled").asBoolean());
        assertTrue(bVersions.get(0).path("isDefault").asBoolean());
        assertTrue(fail(call(put(API + "/versions/" + v2Id + "/default"), b)).path("message").asText().contains("不存在"));
        assertTrue(fail(call(get(API + "/versions/" + v2Id + "/file"), b)).path("message").asText().contains("不存在"));

        // 下载与按版本预览导出 Excel
        MockHttpServletResponse file = perform(get(API + "/versions/" + v2Id + "/file"), a);
        assertEquals(XLSX, file.getContentType());
        MockHttpServletResponse preview = perform(get(API + "/versions/" + v2Id + "/preview").param("format", "xlsx"), a);
        assertEquals(XLSX, preview.getContentType());
        assertTrue(preview.getContentAsByteArray().length > 0);
    }

    @Test
    void wrongFileTypeAndPlaceholderErrors() throws Exception {
        String a = token("it_doc_admin", TENANT_A);
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        assertTrue(fail(upload(a, 1, "logo.xlsx", png, "错文件")).path("message").asText().contains("Excel"));

        JsonNode bad = fail(upload(a, 1, "bad.xlsx", xlsxWith("${customer.nmae}"), "拼错"));
        assertEquals("TEMPLATE_INVALID", bad.path("data").path("errorCode").asText());
        String detail = bad.path("data").path("detail").toString();
        assertTrue(detail.contains("B3") && detail.contains("${customer.nmae}"), detail);
        assertTrue(detail.contains("没有找到明细行"), detail);

        assertTrue(fail(upload(a, 1, "q.xlsx", builtinQuotation(), " ")).path("message").asText().contains("版本说明"));
        assertEquals(1, versions(a, 1).size(), "失败的上传不产生版本");

        // PI、CI、PL 与报价单一样校验占位符与明细行
        assertTrue(fail(upload(a, 2, "pi.xlsx", xlsxWith("anything"), "PI 草稿")).path("data").path("detail").toString()
                .contains("没有找到明细行"));
        assertTrue(fail(upload(a, 2, "pi.xlsx", xlsxWith("${quotation.no} ${item.model}"), "PI 用了报价单字段")).path("data")
                .path("detail").toString().contains("${quotation.no}"));
        ok(upload(a, 2, "pi.xlsx", xlsxWith("${pi.no} ${buyer.name} ${bank.accountNo} ${item.hsCode}"), "PI 占位符"));
        assertTrue(fail(upload(a, 3, "ci.xlsx", xlsxWith("anything"), "CI 草稿")).path("data").path("detail").toString()
                .contains("没有找到明细行"));
        assertTrue(fail(upload(a, 3, "ci.xlsx", xlsxWith("${ci.no} ${box.netWeight} ${item.model}"), "CI 用了箱字段")).path("data")
                .path("detail").toString().contains("${box.netWeight}"));
        ok(upload(a, 3, "ci.xlsx", xlsxWith("${ci.no} ${consignee.name} ${item.model} ${item.hsCode}"), "CI 占位符"));
        ok(upload(a, 4, "pl.xlsx", xlsxWith("${pl.no} ${item.model} ${box.grossWeight}"), "PL 占位符"));
    }

    @Test
    void textTemplateVersion() throws Exception {
        String a = token("it_doc_admin", TENANT_A);
        JsonNode bad = fail(call(post(API + "/text-versions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"${item.model}\",\"note\":\"缺块\"}"), a));
        assertEquals("TEMPLATE_INVALID", bad.path("data").path("errorCode").asText());

        String content = "Dear ${customer.contact},\\n{{#items}}\\n${item.model} ${quotation.currencySymbol}${item.unitPriceShort}\\n{{/items}}";
        JsonNode v2 = ok(call(post(API + "/text-versions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"" + content + "\",\"note\":\"加称呼\"}"), a));
        assertEquals(2, v2.path("versionNo").asInt());
        String text = perform(get(API + "/versions/" + v2.path("id").asLong() + "/preview"), a).getContentAsString(StandardCharsets.UTF_8);
        assertTrue(text.startsWith("Dear John Smith"), text);
        assertTrue(text.contains("1756-L83E $6120"), text);
    }

    @Test
    void permissions() throws Exception {
        String viewer = token("it_doc_viewer", TENANT_A);
        ok(call(get(API), viewer));
        assertEquals(403, perform(multipart(API + "/1/versions")
                .file(new MockMultipartFile("file", "q.xlsx", XLSX, builtinQuotation())).param("note", "x"), viewer).getStatus());

        String platform = token("it_doc_admin", 0);
        long v1 = versions(platform, 1).get(0).path("id").asLong();
        assertTrue(fail(call(put(API + "/versions/" + v1 + "/default"), platform)).path("message").asText().contains("平台内置"));
    }
}
