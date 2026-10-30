package com.zhul.erp.modules.inquiry;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.modules.inquiry.customerinquiry.service.CustomerInquiryService;
import com.zhul.erp.modules.inquiry.support.PriceKeys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** 询盘单（spec inquiry/inquiry-intake）：录入、新老客户、手动录入、AI 解析回调、确认与历史价复用、取消、数据权限 */
class InquiryIntakeContractTest extends InquiryContractSupport {

    private static final long ADMIN_ID = 99000002L;

    @Autowired private CustomerInquiryService inquiryService;
    @Autowired private PriceKeys priceKeys;

    private long submit(String token, long customerId, String extra) throws Exception {
        return ok(call(json(post(INQ), "{\"customerId\":" + customerId + ",\"source\":1,\"rawContent\":\"please quote\"" + extra + "}"), token))
                .path("id").asLong();
    }

    private static Map<String, Object> row(String brand, String category, String model, int qty, Long reuse) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("brand", brand);
        m.put("category", category);
        m.put("confirmedModel", model);
        m.put("quantity", qty);
        m.put("reuseQuoteId", reuse);
        return m;
    }

    /** 插一条已提交的询价记录（询价记录） */
    private long quote(String brand, String model, String price, int condition, String quotedAt) {
        jdbc.update("insert into sourcing_quote (tenant_id, brand, brand_key, model, model_key, channel, shop_name, currency_code, unit_price, "
                        + "exchange_rate, unit_price_cny, item_condition, quoted_by, quoted_at, status) values (0, ?, ?, ?, ?, 2, '明和电气', 'CNY', ?, 1, ?, ?, ?, ?, 2)",
                brand, priceKeys.brand(brand).brandKey(), model, PriceKeys.model(model), price, price, condition, BUYER_LIN, quotedAt);
        return jdbc.queryForObject("select max(id) from sourcing_quote", Long.class);
    }

    @Test
    void submit_validatesContentDeadlineAndAttachment() throws Exception {
        loginAsAdmin("it_inq_admin");
        String admin = token("it_inq_admin");
        long c = customer("Wingrid Rocha", "Brazil");

        assertEquals("请粘贴询盘内容或上传附件",
                call(json(post(INQ), "{\"customerId\":" + c + ",\"source\":1}"), admin).path("message").asText());
        assertEquals("报价截止不能早于询盘日期",
                call(json(post(INQ), "{\"customerId\":" + c + ",\"source\":1,\"rawContent\":\"x\",\"inquiryDate\":\"2026-10-01\",\"quoteDeadline\":\"2026-09-30\"}"),
                        admin).path("message").asText());
        assertEquals("来源渠道不正确",
                call(json(post(INQ), "{\"customerId\":" + c + ",\"source\":99,\"rawContent\":\"x\"}"), admin)
                        .path("message").asText());
        JsonNode channels = ok(call(get("/api/v1/system/dict-items").param("dictType", "crm_source_channel"), admin));
        assertEquals(8, channels.size(), "来源渠道字典与商机、客户共用 8 项");
        assertEquals("阿里巴巴国际站", channels.path(0).path("itemName").asText());
        JsonNode fake = call(multipart(INQ + "/attachments").file(new MockMultipartFile("file", "rfq.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "MZ\u0090\u0000fake exe".getBytes(StandardCharsets.ISO_8859_1))), admin);
        assertTrue(fake.path("message").asText().startsWith("文件类型不支持"), fake.toString());

        JsonNode created = ok(call(json(post(INQ), "{\"customerId\":" + c + ",\"source\":1,\"rawContent\":\"Siemens 6ES7214-1AG40-0XB0 x2\"}"), admin));
        assertTrue(created.path("inquiryCode").asText().matches("IQ\\d{11}"), created.toString());
        assertEquals(1, created.path("status").asInt());
        assertEquals(ADMIN_ID, created.path("ownerId").asLong());
        assertEquals(LocalDate.now().toString(), created.path("quoteDeadline").asText(), "报价截止默认当天");
        assertEquals(1, created.path("customerType").asInt());
    }

    /** 询盘的图片附件确认型号后进素材库「客户来图」，标注询盘单的全部型号，默认仅内部；Excel 询价清单不算素材 */
    @Test
    void confirm_indexesImageAttachmentsAsCustomerImages() throws Exception {
        loginAsAdmin("it_inq_admin");
        String admin = token("it_inq_admin");
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R'};
        JsonNode img = ok(call(multipart(INQ + "/attachments").file(new MockMultipartFile("file", "铭牌.png", "image/png", png)), admin));
        JsonNode csv = ok(call(multipart(INQ + "/attachments").file(new MockMultipartFile("file", "清单.csv", "text/csv",
                "model,qty\nE3Z-D61,1".getBytes(StandardCharsets.UTF_8))), admin));
        String files = ",\"attachments\":[{\"fileKey\":\"" + img.path("fileKey").asText() + "\",\"fileName\":\"铭牌.png\"},"
                + "{\"fileKey\":\"" + csv.path("fileKey").asText() + "\",\"fileName\":\"清单.csv\"}]";
        long id = submit(admin, customer("Wingrid Rocha", "Brazil"), files);
        ok(call(json(post(INQ + "/" + id + "/confirm"), write(Map.of("rows", List.of(
                row("Omron", "传感器", "E3Z-D61", 1, null), row("Siemens", "PLC", "6ES7214-1AG40-0XB0", 2, null))))), admin));
        List<String> items = jdbc.queryForList("select concat(m.category, ':', m.can_send, ':', m.asset_key) from media_item m "
                + "join customer_inquiry_attachment a on a.id = m.source_id where m.source_table = 'customer_inquiry_attachment' "
                + "and a.customer_inquiry_id = ? and m.deleted_at is null order by m.asset_key", String.class, id);
        assertEquals(List.of("3:0:omron e3z-d61", "3:0:siemens 6es7214-1ag40-0xb0"), items);
        jdbc.update("delete from media_item where source_table = 'customer_inquiry_attachment' and source_id in "
                + "(select id from customer_inquiry_attachment where customer_inquiry_id = ?)", id);
    }

    @Test
    void customerType_snapshotFromWonInquiries() throws Exception {
        loginAsAdmin("it_inq_admin");
        String admin = token("it_inq_admin");
        long returning = customer("Delta Industrial", "United States");
        long asked = customer("Ahmed Hassan", "Egypt");
        for (int i = 0; i < 3; i++) {
            jdbc.update("insert into customer_inquiry (tenant_id, inquiry_code, customer_id, inquiry_date, quote_deadline, status) values (0, ?, ?, curdate(), curdate(), 8)",
                    "ITWON" + i, returning);
        }
        jdbc.update("insert into customer_inquiry (tenant_id, inquiry_code, customer_id, inquiry_date, quote_deadline, status) values (0, 'ITASK1', ?, curdate(), curdate(), 7)", asked);

        JsonNode type = ok(call(get(INQ + "/customer-type").param("customerId", String.valueOf(returning)), admin));
        assertEquals(2, type.path("customerType").asInt());
        assertEquals(3, type.path("wonCount").asInt());
        long newOne = submit(admin, returning, "");
        assertEquals(2, ok(call(get(INQ + "/" + newOne), admin)).path("inquiry").path("customerType").asInt());
        long fromAsked = submit(admin, asked, "");
        assertEquals(1, ok(call(get(INQ + "/" + fromAsked), admin)).path("inquiry").path("customerType").asInt(), "只询过没成交的仍是新客户");

        jdbc.update("update customer_inquiry set status = 8 where id = ?", fromAsked);
        assertEquals(1, ok(call(get(INQ + "/" + fromAsked), admin)).path("inquiry").path("customerType").asInt(), "标记不随后续成交改变");
        assertEquals(2, ok(call(get(INQ + "/" + submit(admin, asked, "")), admin)).path("inquiry").path("customerType").asInt());
        assertEquals(2, ok(call(json(post(INQ + "/page"), "{\"customerType\":2}"), admin)).path("total").asInt());
    }

    @Test
    void manualEntry_reuseHistoryAndSplitByBrandCategory() throws Exception {
        loginAsAdmin("it_inq_admin");
        String admin = token("it_inq_admin");
        long cheap = quote("Siemens", "6ES7 214-1AG40-0XB0", "1350.00", 1, "2026-09-22 10:00:00");
        quote("Siemens", "6ES7214-1AG40-0XB0", "1480.00", 1, "2026-08-15 10:00:00");
        long other = quote("Shihlin", "6ES72141AG400XB0", "500.00", 1, "2026-09-01 10:00:00");

        long id = submit(admin, customer("Wingrid Rocha", "Brazil"), "");
        assertEquals(0, ok(call(get(INQ + "/" + id + "/draft"), admin)).path("rows").size(), "手动录入从空表开始");
        assertEquals(1, ok(call(get(INQ + "/" + id), admin)).path("inquiry").path("status").asInt(),
                "打开手动录入、什么都没填时仍是待解析，刷新后不会变成待确认");

        JsonNode match = ok(call(get(INQ + "/price-match").param("brand", "SIEMENS").param("model", "6es7214 1ag40 0xb0"), admin));
        assertEquals(2, match.path("sameBrand").size(), "写法不同的同一型号归到一起");
        assertEquals(cheap, match.path("defaultQuoteId").asLong(), "默认全新原装中最低价");
        assertEquals(other, match.path("otherBrands").get(0).path("id").asLong(), "其他品牌只作参考");

        Map<String, Object> badLifecycle = new java.util.HashMap<>(row("SIEMENS", "PLC", "6es7214 1ag40 0xb0", 2, null));
        badLifecycle.put("lifecycle", 9);
        assertEquals("第 1 行生命周期不在可选值内，请从下拉中选择",
                call(json(post(INQ + "/" + id + "/confirm"), write(Map.of("rows", List.of(badLifecycle)))), admin).path("message").asText(),
                "生命周期只能取字典里启用的选项");
        List<Map<String, Object>> wrong = List.of(row("SIEMENS", "PLC", "6es7214 1ag40 0xb0", 2, other));
        assertEquals("第 1 行选择的历史价格与品牌、型号不一致，请重新选择",
                call(json(post(INQ + "/" + id + "/confirm"), write(Map.of("rows", wrong))), admin).path("message").asText());

        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row("SIEMENS", "PLC", "6es7214 1ag40 0xb0", 2, cheap));
        rows.add(row("Mitsubishi", "伺服电机", "HG-SN302BJ", 1, null));
        rows.add(row("Delta", "HMI", "DOP-110WS", 5, null));
        rows.add(row("Mitsubishi", "伺服电机", "HG-SN202BJ", 1, null));
        rows.add(row("Mitsubishi", "变频器", "FR-D740-0.4K", 1, null));
        rows.add(row("Mitsubishi", "伺服电机", "HG-SN102BJ", 2, null));
        rows.add(row("Delta", "HMI", "DOP-107BV", 2, null));
        ok(call(json(post(INQ + "/" + id + "/confirm"), write(Map.of("rows", rows))), admin));

        JsonNode detail = ok(call(get(INQ + "/" + id), admin));
        JsonNode inq = detail.path("inquiry");
        assertEquals(5, inq.path("status").asInt(), "有待询价型号时进入询价中");
        assertEquals(7, inq.path("totalItemCount").asInt());
        assertEquals(1, inq.path("pricedItemCount").asInt());
        assertEquals(3, inq.path("taskCount").asInt());
        JsonNode tasks = detail.path("tasks");
        String code = inq.path("inquiryCode").asText();
        assertEquals(code + "A", tasks.get(0).path("taskCode").asText());
        assertEquals(3, tasks.get(0).path("itemCount").asInt(), "三菱·伺服三个型号一组");
        assertEquals("Delta", tasks.get(1).path("brand").asText());
        assertEquals(2, tasks.get(1).path("itemCount").asInt());
        assertEquals("变频器与驱动", tasks.get(2).path("category").asText(), "「变频器」关联到品类后，任务按一级品类命名");
        JsonNode items = detail.path("items");
        assertEquals("SIEMENS", items.get(0).path("brand").asText());
        assertEquals(1, items.get(0).path("priceSource").asInt());
        assertEquals(0, new java.math.BigDecimal("1350.00").compareTo(items.get(0).path("selectedQuote").path("unitPriceCny").decimalValue()));
        assertEquals("伺服电机", items.get(1).path("category").asText());
        assertEquals("伺服电机", items.get(2).path("category").asText(), "同品牌同品类的型号排在一起");
        assertEquals("伺服电机", items.get(3).path("category").asText());
    }

    @Test
    void totalQuantity_filterAndSortByModelCountAndQuantity() throws Exception {
        loginAsAdmin("it_inq_admin");
        String admin = token("it_inq_admin");
        long single = submit(admin, customer("Single Buyer", "Germany"), "");
        ok(call(json(post(INQ + "/" + single + "/confirm"), write(Map.of("rows", List.of(row("Omron", "传感器", "E3Z-D61", 1, null))))), admin));
        long bulk = submit(admin, customer("Bulk Buyer", "Germany"), "");
        ok(call(json(post(INQ + "/" + bulk + "/confirm"), write(Map.of("rows", List.of(
                row("Omron", "传感器", "E3Z-D61", 10, null), row("Omron", "传感器", "E3Z-D62", 5, null),
                row("Delta", "HMI", "DOP-110WS", 2, null))))), admin));
        long mid = submit(admin, customer("Mid Buyer", "Germany"), "");
        ok(call(json(post(INQ + "/" + mid + "/confirm"), write(Map.of("rows", List.of(
                row("ABB", "变频器", "ACS580-01-12A7-4", 3, null), row("ABB", "变频器", "ACS580-01-17A0-4", 4, null))))), admin));

        JsonNode b = ok(call(get(INQ + "/" + bulk), admin)).path("inquiry");
        assertEquals(3, b.path("totalItemCount").asInt());
        assertEquals(17, b.path("totalQuantity").asInt(), "所有型号数量相加");

        JsonNode valuable = ok(call(json(post(INQ + "/page"), "{\"minItemCount\":2,\"minTotalQuantity\":10}"), admin));
        assertEquals(1, valuable.path("total").asInt(), "多型号且总数量大的询盘");
        assertEquals(bulk, valuable.path("records").get(0).path("id").asLong());

        JsonNode byQty = ok(call(json(post(INQ + "/page"), "{\"sortField\":\"totalQuantity\",\"sortOrder\":\"desc\"}"), admin)).path("records");
        assertEquals(List.of(bulk, mid, single), List.of(byQty.get(0).path("id").asLong(), byQty.get(1).path("id").asLong(), byQty.get(2).path("id").asLong()),
                "按总数量倒序");
        JsonNode byCount = ok(call(json(post(INQ + "/page"), "{\"sortField\":\"totalItemCount\",\"sortOrder\":\"asc\",\"maxItemCount\":2}"), admin)).path("records");
        assertEquals(List.of(single, mid), List.of(byCount.get(0).path("id").asLong(), byCount.get(1).path("id").asLong()),
                "型号数不超过 2，按型号数正序");
        JsonNode ignored = ok(call(json(post(INQ + "/page"), "{\"sortField\":\"id; drop table\"}"), admin));
        assertEquals(3, ignored.path("total").asInt(), "不认识的排序字段忽略，按创建时间倒序");
    }

    @Test
    void level_defaultValidateUpdateWithLogAndFilter() throws Exception {
        loginAsAdmin("it_inq_admin");
        String admin = token("it_inq_admin");
        long c = customer("Level Buyer", "Germany");
        long normal = submit(admin, c, "");
        assertEquals(3, ok(call(get(INQ + "/" + normal), admin)).path("inquiry").path("level").asInt(), "不传等级按 B");
        assertEquals("询盘等级不正确", call(json(post(INQ), "{\"customerId\":" + c + ",\"source\":1,\"rawContent\":\"x\",\"level\":9}"), admin)
                .path("message").asText());
        long top = submit(admin, c, ",\"level\":1");
        assertEquals(1, ok(call(get(INQ + "/" + top), admin)).path("inquiry").path("level").asInt());

        String logSql = "select count(*) from sys_log where operation = '调整询盘等级'";
        int logsBefore = jdbc.queryForObject(logSql, Integer.class);
        ok(call(json(put(INQ + "/" + normal + "/level"), "{\"level\":2}"), admin));
        assertEquals(2, ok(call(get(INQ + "/" + normal), admin)).path("inquiry").path("level").asInt());
        assertEquals(logsBefore + 1, jdbc.queryForObject(logSql, Integer.class), "调整等级留痕");
        ok(call(json(put(INQ + "/" + normal + "/level"), "{\"level\":2}"), admin));
        assertEquals(logsBefore + 1, jdbc.queryForObject(logSql, Integer.class), "等级没变时不记日志");
        assertEquals("询盘等级不正确", call(json(put(INQ + "/" + normal + "/level"), "{\"level\":0}"), admin).path("message").asText());

        assertEquals(top, ok(call(json(post(INQ + "/page"), "{\"level\":1}"), admin)).path("records").get(0).path("id").asLong());
        JsonNode byLevel = ok(call(json(post(INQ + "/page"), "{\"sortField\":\"level\",\"sortOrder\":\"desc\"}"), admin)).path("records");
        assertEquals(List.of(top, normal), List.of(byLevel.get(0).path("id").asLong(), byLevel.get(1).path("id").asLong()), "等级从高到低：S 在前");
    }

    @Test
    void allReused_goesStraightToReadyToQuote() throws Exception {
        loginAsAdmin("it_inq_admin");
        String admin = token("it_inq_admin");
        long q = quote("Omron", "E3Z-D61", "86.00", 1, "2026-09-28 10:00:00");
        long id = submit(admin, customer("Nordic Packaging", "Sweden"), "");
        ok(call(json(post(INQ + "/" + id + "/confirm"), write(Map.of("rows", List.of(row("OMRON", "传感器", "E3ZD61", 10, q))))), admin));
        JsonNode detail = ok(call(get(INQ + "/" + id), admin));
        assertEquals(6, detail.path("inquiry").path("status").asInt());
        assertEquals(0, detail.path("tasks").size());
    }

    /** 测试专用品类（编码前缀 itq_），不依赖种子数据 */
    private long itqCategory(String code, String zh, Long parentId) {
        jdbc.update("insert into product_category (tenant_id, category_code, category_name, category_name_zh, parent_id, status, create_by) "
                + "values (0, ?, ?, ?, ?, 1, 'itq')", code, code, zh, parentId);
        return jdbc.queryForObject("select id from product_category where tenant_id = 0 and category_code = ?", Long.class, code);
    }

    private void cleanupItqCategories() {
        jdbc.update("delete from product_category where category_code like 'itq\\_%' and parent_id is not null");
        jdbc.update("delete from product_category where category_code like 'itq\\_%'");
    }

    @Test
    void aiParse_linksCategoryByCodeOrText_confirmLinksByIdOrText() throws Exception {
        cleanupItqCategories();
        try {
            long ctrl = itqCategory("itq_ctrl", "仝丁控制器", null);
            long io = itqCategory("itq_io", "仝丁输入模块", ctrl);
            itqCategory("itq_cpu", "仝丁处理单元", ctrl);
            long motion = itqCategory("itq_motion", "仝戊运动", null);
            long motor = itqCategory("itq_motor", "仝戊电机", motion);
            itqCategory("itq_drive", "仝戊驱动器", motion);
            linkCategories(ctrl, io, motor);
        } finally {
            cleanupItqCategories();
        }
    }

    private void linkCategories(long ctrl, long io, long motor) throws Exception {
        loginAsAdmin("it_inq_admin");
        String admin = token("it_inq_admin");
        long id = submit(admin, customer("Delta Automation", "Chile"), "");
        String output = "{\"groups\":[{\"brand\":\"Siemens\",\"category\":\"仝丁控制器\",\"items\":["
                + "{\"categoryCode\":\"itq_io\",\"originalModel\":\"6ES7321-1BL00-0AA0\",\"confidence\":1,\"quantity\":2},"
                + "{\"categoryCode\":\"no_such_code\",\"originalModel\":\"6ES7214-1AG40-0XB0\",\"confidence\":1,\"quantity\":1}]},"
                + "{\"brand\":\"Mitsubishi\",\"category\":\"仝戊电机\",\"items\":["
                + "{\"originalModel\":\"HG-SN302BJ\",\"confidence\":1,\"quantity\":1}]},"
                + "{\"brand\":\"NSK\",\"category\":\"仝无此类\",\"items\":["
                + "{\"originalModel\":\"6205ZZ\",\"confidence\":1,\"quantity\":4}]}]}";
        jdbc.update("insert into ai_task (tenant_id, skill_id, status, output, requested_by) values (0, 'inquiry-parse-and-split', 3, ?, ?)", output, ADMIN_ID);
        long task = jdbc.queryForObject("select max(id) from ai_task", Long.class);
        jdbc.update("update customer_inquiry set status = 2, ai_task_id = ? where id = ?", task, id);
        inquiryService.applyParseSuccess(task, output);

        JsonNode rows = ok(call(get(INQ + "/" + id + "/draft"), admin)).path("rows");
        assertEquals(io, rows.get(0).path("categoryId").asLong(), "按 AI 给的编码关联细分品类");
        assertEquals("仝丁输入模块", rows.get(0).path("category").asText(), "品类文字为所关联品类的中文名");
        assertEquals(ctrl, rows.get(1).path("categoryId").asLong(), "编码无效时按分组品类文字匹配到一级");
        assertEquals(motor, rows.get(2).path("categoryId").asLong(), "没给编码时按文字匹配到细分");
        assertFalse(rows.get(3).hasNonNull("categoryId"), "关联不上时为空：" + rows.get(3));
        assertEquals("仝无此类", rows.get(3).path("category").asText(), "关联不上时保留原文");

        long manual = submit(admin, customer("Rocha Engenharia", "Brazil"), "");
        Map<String, Object> picked = new LinkedHashMap<>(row("Siemens", "随便写的", "6ES7321-1BL00-0AA0", 1, null));
        picked.put("categoryId", io);
        ok(call(json(post(INQ + "/" + manual + "/confirm"), write(Map.of("rows", List.of(picked,
                row("Mitsubishi", "仝戊电机", "HG-SN202BJ", 1, null), row("NSK", "仝无此类", "6206ZZ", 1, null))))), admin));
        List<Map<String, Object>> saved = jdbc.queryForList(
                "select category, category_id from inquiry_item where customer_inquiry_id = ? order by line_no", manual);
        assertEquals(io, ((Number) saved.get(0).get("category_id")).longValue(), "下拉选中的品类优先");
        assertEquals("仝丁输入模块", saved.get(0).get("category"));
        assertEquals(motor, ((Number) saved.get(1).get("category_id")).longValue(), "手填文字按规则关联");
        assertNull(saved.get(2).get("category_id"), "关联不上只留文字");
        assertEquals("仝无此类", saved.get(2).get("category"));
    }

    @Test
    void aiParse_successFillsDraft_failureCanRetryOrSwitchToManual() throws Exception {
        loginAsAdmin("it_inq_admin");
        String admin = token("it_inq_admin");
        long id = submit(admin, customer("Pacific Controls", "Australia"), "");
        String output = "{\"groups\":[{\"brand\":\"DROPSA\",\"category\":\"其他\",\"inquiryTemplate\":\"帮我查一下 DROPSA 的过滤器\",\"items\":["
                + "{\"originalModel\":\"20905\",\"confirmedModel\":\"20905\",\"confidence\":1,\"quantity\":3,\"unit\":\"个\",\"lifecycle\":2,"
                + "\"replacementModel\":\"2043103\",\"difficulty\":2,\"searchKeywords\":[\"20905\",\"DROPSA 20905\"]},"
                + "{\"originalModel\":\"2043103\",\"confidence\":3,\"quantity\":3}]}]}";
        jdbc.update("insert into ai_task (tenant_id, skill_id, status, output, requested_by) values (0, 'inquiry-parse-and-split', 3, ?, ?)", output, ADMIN_ID);
        long task = jdbc.queryForObject("select max(id) from ai_task", Long.class);
        jdbc.update("update customer_inquiry set status = 2, ai_task_id = ? where id = ?", task, id);

        inquiryService.applyParseSuccess(task, output);
        JsonNode draft = ok(call(get(INQ + "/" + id + "/draft"), admin));
        assertEquals(3, draft.path("inquiry").path("status").asInt());
        assertEquals(6, draft.path("inquiry").path("totalQuantity").asInt(), "待确认时总数量为 AI 识别结果之和");
        JsonNode first = draft.path("rows").get(0);
        assertEquals(2, first.path("lifecycle").asInt());
        assertEquals("2043103", first.path("replacementModel").asText());
        assertEquals("帮我查一下 DROPSA 的过滤器", first.path("inquiryScript").asText(), "型号没有单独话术时用分组话术");
        assertEquals("2043103", draft.path("rows").get(1).path("confirmedModel").asText(), "确认型号缺失时用原始型号");
        assertEquals(3, draft.path("rows").get(1).path("lifecycle").asInt(), "生命周期缺失时为待查");
        assertEquals(1, draft.path("inquiry").path("pendingVerifyCount").asInt());

        long failed = submit(admin, customer("Erik Larsson", "Sweden"), "");
        jdbc.update("update customer_inquiry set status = 2, ai_task_id = ? where id = ?", task + 1000, failed);
        inquiryService.applyParseFailure(task + 1000, "AI 超时未返回结果");
        JsonNode d = ok(call(get(INQ + "/" + failed), admin));
        assertEquals(4, d.path("inquiry").path("status").asInt());
        assertEquals("AI 超时未返回结果", d.path("parseError").asText());
        assertEquals("只有待解析的询盘可以发起 AI 解析", call(post(INQ + "/" + failed + "/start-parse"), admin).path("message").asText());
        assertEquals(4, ok(call(get(INQ + "/" + failed), admin)).path("inquiry").path("status").asInt());
        ok(call(json(post(INQ + "/" + failed + "/confirm"), write(Map.of("rows", List.of(row("ABB", "变频器", "ACS580-01-12A7-4", 1, null))))), admin));
        JsonNode manual = ok(call(get(INQ + "/" + failed), admin));
        assertEquals(5, manual.path("inquiry").path("status").asInt(), "解析失败后手动录入，确认时直接进入询价中");
        assertEquals(2, manual.path("inquiry").path("parseMode").asInt(), "记为手动录入");
        assertEquals("", manual.path("parseError").asText(""), "手动确认后清掉解析失败原因");
    }

    @Test
    void cancel_cancelsTasks_andNotAfterQuoted() throws Exception {
        loginAsAdmin("it_inq_admin");
        String admin = token("it_inq_admin");
        long id = submit(admin, customer("Al Noor", "United Arab Emirates"), "");
        ok(call(json(post(INQ + "/" + id + "/confirm"), write(Map.of("rows", List.of(row("ABB", "变频器", "ACS580-01-12A7-4", 1, null))))), admin));
        ok(call(put(INQ + "/" + id + "/cancel"), admin));
        assertEquals(4, jdbc.queryForObject("select status from sourcing_task where customer_inquiry_id = ?", Integer.class, id));
        assertEquals(10, ok(call(get(INQ + "/" + id), admin)).path("inquiry").path("status").asInt());

        long quoted = submit(admin, customer("Bangla Power", "Bangladesh"), "");
        jdbc.update("update customer_inquiry set status = 7 where id = ?", quoted);
        assertEquals("已报价的询盘不能取消", call(put(INQ + "/" + quoted + "/cancel"), admin).path("message").asText());
    }

    @Test
    void delete_onlyWithoutDownstream_cancelledTasksGoAlong_needsButton() throws Exception {
        loginAsAdmin("it_inq_admin");
        String admin = token("it_inq_admin");
        long fresh = submit(admin, customer("Gulf Pumps", "Oman"), "");
        assertEquals(0, ok(call(get(INQ + "/" + fresh + "/downstream"), admin)).size());
        ok(call(delete(INQ + "/" + fresh), admin));
        assertEquals("询盘单不存在", call(get(INQ + "/" + fresh), admin).path("message").asText());
        assertEquals(1, jdbc.queryForObject("select count(*) from customer_inquiry where id = ? and deleted_at is not null", Integer.class, fresh),
                "软删除");

        // 有询价任务时列出来、不能删；取消询盘后任务为已取消，可以删，任务一并删除
        long sourcing = submit(admin, customer("Delta Mart", "Kenya"), "");
        ok(call(json(post(INQ + "/" + sourcing + "/confirm"), write(Map.of("rows", List.of(row("ABB", "变频器", "ACS580-01-12A7-4", 1, null))))), admin));
        JsonNode down = ok(call(get(INQ + "/" + sourcing + "/downstream"), admin));
        assertEquals(1, down.size());
        assertEquals("TASK", down.get(0).path("kind").asText());
        assertEquals("待分配", down.get(0).path("statusName").asText());
        assertEquals("这条询盘已有询价任务、报价单、PI 或销售订单引用，不能删除", call(delete(INQ + "/" + sourcing), admin).path("message").asText());
        ok(call(put(INQ + "/" + sourcing + "/cancel"), admin));
        ok(call(delete(INQ + "/" + sourcing), admin));
        assertEquals(0, jdbc.queryForObject("select count(*) from sourcing_task where customer_inquiry_id = ? and deleted_at is null", Integer.class, sourcing));

        // 报价单引用：作废后不再算
        long quoted = submit(admin, customer("Cairo Steel", "Egypt"), "");
        jdbc.update("delete from quotation where tenant_id = 0 and quotation_no = 'QT20261030901'");
        jdbc.update("insert into quotation (tenant_id, quotation_no, status) values (0, 'QT20261030901', 2)");
        long q = jdbc.queryForObject("select max(id) from quotation where quotation_no = 'QT20261030901'", Long.class);
        try {
            jdbc.update("insert into quotation_item (tenant_id, quotation_id, customer_inquiry_id) values (0, ?, ?)", q, quoted);
            down = ok(call(get(INQ + "/" + quoted + "/downstream"), admin));
            assertEquals("QUOTATION", down.get(0).path("kind").asText());
            assertEquals("QT20261030901", down.get(0).path("no").asText());
            assertEquals("已发送", down.get(0).path("statusName").asText());
            jdbc.update("update quotation set status = 5 where id = ?", q);
            assertEquals(0, ok(call(get(INQ + "/" + quoted + "/downstream"), admin)).size());
        } finally {
            jdbc.update("delete from quotation_item where quotation_id = ?", q);
            jdbc.update("delete from quotation where id = ?", q);
        }

        // 没有「删除询盘」按钮权限的不能删
        loginWithResources("it_inq_staff", MENU_INQUIRY);
        String staff = token("it_inq_staff");
        long own = submit(staff, customer("Riyadh Valves", "Saudi Arabia"), "");
        assertEquals(403, perform(delete(INQ + "/" + own), staff).getStatus());
    }

    @Test
    void dataScope_andPartTimerCannotAccess() throws Exception {
        loginAsAdmin("it_inq_admin");
        String admin = token("it_inq_admin");
        long others = submit(admin, customer("Wingrid Rocha", "Brazil"), "");
        long mine = submit(admin, customer("Delta Industrial", "United States"), "");
        jdbc.update("update customer_inquiry set owner_id = ? where id = ?", BUYER_LIN, others);

        loginWithResources("it_inq_staff", MENU_INQUIRY);
        String staff = token("it_inq_staff");
        assertEquals("询盘单不存在", call(get(INQ + "/" + others), staff).path("message").asText());
        JsonNode page = ok(call(json(post(INQ + "/page"), "{}"), staff));
        assertEquals(1, page.path("total").asInt());
        assertEquals(mine, page.path("records").get(0).path("id").asLong());

        assertEquals(403, perform(json(post(INQ + "/page"), "{}"), token("it_wang")).getStatus(), "兼职采购不能访问询盘单");
        assertFalse(page.path("records").get(0).path("createTime").isMissingNode());
    }
}
