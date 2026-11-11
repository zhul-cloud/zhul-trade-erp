package com.zhul.erp.modules.inquiry;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.aitask.service.AiOrchestratorClient;
import com.zhul.erp.modules.inquiry.customerinquiry.service.CustomerInquiryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 铭牌图片（spec inquiry/inquiry-intake「铭牌图片」、inquiry/sourcing-quote「采购查看铭牌图片」、
 * inquiry/sourcing-task「询价分配查看铭牌图片」、product/media-library「素材来源与分类」）
 */
class InquiryNameplateContractTest extends InquiryContractSupport {

    private static final long ADMIN_ID = 99000002L;
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R'};
    private static final byte[] PDF = "%PDF-1.4 fake".getBytes();

    @Autowired private CustomerInquiryService inquiryService;
    @MockBean private AiOrchestratorClient orchestrator;

    private String admin;

    @AfterEach
    void clearMedia() {
        jdbc.update("delete from media_item where tenant_id = 0 and source_table = 'customer_inquiry_attachment'");
    }

    private String upload(String name, byte[] bytes, String type) throws Exception {
        return ok(call(multipart(INQ + "/attachments").file(new MockMultipartFile("file", name, type, bytes)), admin)).path("fileKey").asText();
    }

    private static String refs(String field, List<String> keys) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < keys.size(); i++) {
            parts.add("{\"fileKey\":\"" + keys.get(i) + "\",\"fileName\":\"" + field + (i + 1) + ".png\"}");
        }
        return ",\"" + field + "\":[" + String.join(",", parts) + "]";
    }

    private JsonNode submit(long customerId, String body) throws Exception {
        return call(json(post(INQ), "{\"customerId\":" + customerId + ",\"source\":1" + body + "}"), admin);
    }

    private static Map<String, Object> row(String brand, String model, List<Long> plates) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("brand", brand);
        m.put("category", "PLC");
        m.put("confirmedModel", model);
        m.put("quantity", 2);
        m.put("nameplateIds", plates);
        return m;
    }

    private List<Long> plateIds(long inquiryId) {
        return jdbc.queryForList("select id from customer_inquiry_attachment where customer_inquiry_id = ? and kind = 2 order by sort, id",
                Long.class, inquiryId);
    }

    private long itemId(long inquiryId, int lineNo) {
        return jdbc.queryForObject("select id from inquiry_item where customer_inquiry_id = ? and line_no = ?", Long.class, inquiryId, lineNo);
    }

    private List<String> media(long attachmentId) {
        return jdbc.queryForList("select asset_key from media_item where source_table = 'customer_inquiry_attachment' and source_id = ? "
                + "and deleted_at is null order by asset_key", String.class, attachmentId);
    }

    /** 询盘单：一个询盘附件、三张铭牌图片，AI 给出对应关系后进入待确认 */
    private long parsedInquiry(String output) throws Exception {
        long c = customer("Ahmed Trading", "UAE");
        String chat = upload("chat.png", PNG, "image/png");
        List<String> plates = List.of(upload("a.png", PNG, "image/png"), upload("b.png", PNG, "image/png"), upload("c.png", PNG, "image/png"));
        long id = ok(submit(c, ",\"rawContent\":\"see photos\"" + refs("attachments", List.of(chat)) + refs("nameplates", plates))).path("id").asLong();
        jdbc.update("insert into ai_task (tenant_id, skill_id, status, output, requested_by) values (0, 'inquiry-parse-and-split', 3, ?, ?)", output, ADMIN_ID);
        long task = jdbc.queryForObject("select max(id) from ai_task", Long.class);
        jdbc.update("update customer_inquiry set status = 2, ai_task_id = ? where id = ?", task, id);
        // 真实回调按任务所属租户处理（AiTaskServiceImpl.handleCallback）
        TenantContext.setTenantId(0);
        inquiryService.applyParseSuccess(task, output);
        return id;
    }

    private static final String OUTPUT = """
            {"groups":[{"brand":"Siemens","category":"PLC","items":[
              {"originalModel":"6ES7214-1AG40-0XB0","confirmedModel":"6ES7214-1AG40-0XB0","confidence":1,"quantity":2,"unit":"个","nameplates":[1]},
              {"originalModel":"6AV2123-2GB03-0AX0","confirmedModel":"6AV2123-2GB03-0AX0","confidence":1,"quantity":2,"unit":"个","nameplates":[2,1]},
              {"originalModel":"6ES7231-4HD32-0XB0","confidence":1,"quantity":1,"unit":"个","nameplates":[9]}
            ]},{"brand":"Omron","category":"传感器","items":[
              {"originalModel":"E3Z-D61","confirmedModel":"E3Z-D61","confidence":1,"quantity":1,"unit":"个"}
            ]}]}""";

    @Test
    void submit_nameplatesOnly_typeAndCount() throws Exception {
        loginAsAdmin("it_np_admin");
        admin = token("it_np_admin");
        long c = customer("Ahmed Trading", "UAE");
        String png = upload("a.png", PNG, "image/png");
        JsonNode created = ok(submit(c, refs("nameplates", List.of(png))));
        long id = created.path("id").asLong();
        JsonNode detail = ok(call(get(INQ + "/" + id), admin));
        assertEquals(0, detail.path("attachments").size(), "铭牌图片不算询盘附件");
        assertEquals(1, detail.path("nameplates").size());
        assertEquals(0, detail.path("nameplates").path(0).path("inquiryItemId").asLong());

        // 客户发来的铭牌是 PDF 时也能收（2026-11-11 用户反馈）
        String pdf = upload("plate.pdf", PDF, "application/pdf");
        JsonNode withPdf = ok(submit(c, refs("nameplates", List.of(pdf))));
        JsonNode pdfDetail = ok(call(get(INQ + "/" + withPdf.path("id").asLong()), admin));
        assertEquals(1, pdfDetail.path("nameplates").size(), "PDF 铭牌按铭牌保存");
        String csv = upload("list.csv", "model,qty\nA,1".getBytes(), "text/csv");
        assertEquals("铭牌只支持 JPG、PNG、PDF", submit(c, refs("nameplates", List.of(csv))).path("message").asText());
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            many.add(png);
        }
        assertTrue(submit(c, refs("nameplates", many)).path("message").asText().contains("铭牌图片最多 20 张"));
    }

    @Test
    void parse_sendsNumberedNameplates() throws Exception {
        loginAsAdmin("it_np_admin");
        admin = token("it_np_admin");
        when(orchestrator.submit(any(), anyLong(), any(), any())).thenReturn(new CompletableFuture<>());
        long c = customer("Ahmed Trading", "UAE");
        String chat = upload("chat.png", PNG, "image/png");
        List<String> plates = List.of(upload("a.png", PNG, "image/png"), upload("b.png", PNG, "image/png"));
        long id = ok(submit(c, refs("attachments", List.of(chat)) + refs("nameplates", plates))).path("id").asLong();
        ok(call(post(INQ + "/" + id + "/start-parse"), admin));
        ArgumentCaptor<String> input = ArgumentCaptor.forClass(String.class);
        verify(orchestrator, timeout(5000)).submit(eq("inquiry-parse-and-split"), anyLong(), input.capture(), any());
        JsonNode files = objectMapper.readTree(input.getValue()).path("attachments");
        assertEquals(3, files.size());
        assertEquals("attachment", files.path(0).path("role").asText());
        assertEquals("nameplate", files.path(1).path("role").asText());
        assertEquals("1", files.path(1).path("no").asText());
        assertEquals("2", files.path(2).path("no").asText());
    }

    @Test
    void confirm_savesAiMatches_relinkAndMedia() throws Exception {
        loginAsAdmin("it_np_admin");
        admin = token("it_np_admin");
        long id = parsedInquiry(OUTPUT);
        List<Long> p = plateIds(id);

        JsonNode draft = ok(call(get(INQ + "/" + id + "/draft"), admin));
        assertEquals(3, draft.path("nameplates").size());
        assertEquals(1, draft.path("attachments").size());
        JsonNode rows = draft.path("rows");
        assertEquals(List.of(p.get(0)), ids(rows.path(0).path("nameplateIds")));
        assertEquals(List.of(p.get(1)), ids(rows.path(1).path("nameplateIds")), "同一张图被多行引用时只给第一行");
        assertEquals(List.of(), ids(rows.path(2).path("nameplateIds")), "越界编号忽略");

        // 业务员把第 3 张留作未对应，第 2 行删掉不确认
        ok(call(json(post(INQ + "/" + id + "/confirm"), write(Map.of("rows", List.of(
                row("Siemens", "6ES7214-1AG40-0XB0", List.of(p.get(0))),
                row("Siemens", "6ES7231-4HD32-0XB0", List.of(p.get(1))),
                row("Omron", "E3Z-D61", List.of()))))), admin));
        long item1 = itemId(id, 1);
        long item2 = itemId(id, 2);
        JsonNode detail = ok(call(get(INQ + "/" + id), admin));
        assertEquals(item1, detail.path("nameplates").path(0).path("inquiryItemId").asLong());
        assertEquals(item2, detail.path("nameplates").path(1).path("inquiryItemId").asLong());
        assertEquals(0, detail.path("nameplates").path(2).path("inquiryItemId").asLong());

        assertEquals(List.of("siemens 6es7214-1ag40-0xb0"), media(p.get(0)), "对应型号的铭牌只进那个型号");
        assertEquals(3, media(p.get(2)).size(), "未对应的铭牌标全部型号");
        long chat = jdbc.queryForObject("select id from customer_inquiry_attachment where customer_inquiry_id = ? and kind = 1", Long.class, id);
        assertEquals(3, media(chat).size(), "询盘附件仍标全部型号");

        String logSql = "select count(*) from sys_log where operation = '改铭牌图片对应型号'";
        int logs = jdbc.queryForObject(logSql, Integer.class);
        ok(call(json(put(INQ + "/" + id + "/nameplates/" + p.get(0)), "{\"inquiryItemId\":" + item2 + "}"), admin));
        assertEquals(List.of("siemens 6es7231-4hd32-0xb0"), media(p.get(0)));
        assertEquals(logs + 1, jdbc.queryForObject(logSql, Integer.class));
        ok(call(json(put(INQ + "/" + id + "/nameplates/" + p.get(0)), "{\"inquiryItemId\":0}"), admin));
        assertEquals(0L, jdbc.queryForObject("select inquiry_item_id from customer_inquiry_attachment where id = ?", Long.class, p.get(0)));

        long other = parsedInquiry(OUTPUT);
        ok(call(json(post(INQ + "/" + other + "/confirm"), write(Map.of("rows", List.of(row("Siemens", "6ES7214-1AG40-0XB0", List.of()))))), admin));
        assertEquals("型号不属于这张询盘单", call(json(put(INQ + "/" + id + "/nameplates/" + p.get(0)),
                "{\"inquiryItemId\":" + itemId(other, 1) + "}"), admin).path("message").asText());
        assertEquals("铭牌图片不存在", call(json(put(INQ + "/" + id + "/nameplates/" + chat), "{\"inquiryItemId\":" + item1 + "}"), admin)
                .path("message").asText());
        jdbc.update("update customer_inquiry set status = 10 where id = ?", id);
        assertEquals("已取消的询盘不能修改", call(json(put(INQ + "/" + id + "/nameplates/" + p.get(0)), "{\"inquiryItemId\":" + item1 + "}"), admin)
                .path("message").asText());
    }

    @Test
    void purchasers_seeOnlyTheirTaskPlates() throws Exception {
        loginAsAdmin("it_np_admin");
        admin = token("it_np_admin");
        long id = parsedInquiry(OUTPUT);
        List<Long> p = plateIds(id);
        // 第 1 张对应西门子型号、第 2 张对应欧姆龙型号、第 3 张未对应
        ok(call(json(post(INQ + "/" + id + "/confirm"), write(Map.of("rows", List.of(
                row("Siemens", "6ES7214-1AG40-0XB0", List.of(p.get(0))),
                row("Omron", "E3Z-D61", List.of(p.get(1))))))), admin));
        long siemensTask = jdbc.queryForObject("select sourcing_task_id from inquiry_item where id = ?", Long.class, itemId(id, 1));
        long omronTask = jdbc.queryForObject("select sourcing_task_id from inquiry_item where id = ?", Long.class, itemId(id, 2));
        ok(call(json(post(BOARD + "/assign"), "{\"taskIds\":[" + siemensTask + "],\"assigneeId\":" + PART_TIMER + "}"), admin));
        ok(call(json(post(BOARD + "/assign"), "{\"taskIds\":[" + omronTask + "],\"assigneeId\":" + BUYER_LIN + "}"), admin));
        long chat = jdbc.queryForObject("select id from customer_inquiry_attachment where customer_inquiry_id = ? and kind = 1", Long.class, id);

        String wang = token("it_wang");
        JsonNode mine = ok(call(get(MY + "/" + siemensTask + "/nameplates"), wang));
        assertEquals(1, mine.path("items").size(), "兼职采购也能看到");
        assertEquals(itemId(id, 1), mine.path("items").path(0).path("itemId").asLong());
        assertEquals(p.get(0), mine.path("items").path(0).path("files").path(0).path("id").asLong());
        assertEquals(1, mine.path("unmatched").size());
        assertEquals(p.get(2), mine.path("unmatched").path(0).path("id").asLong());
        assertTrue(ok(call(get(MY + "/" + siemensTask + "/nameplates/" + p.get(0) + "/url"), wang)).path("url").asText().length() > 0);
        assertTrue(ok(call(get(MY + "/" + siemensTask + "/nameplates/" + p.get(2) + "/url"), wang)).path("url").asText().length() > 0);
        assertEquals("铭牌图片不存在", call(get(MY + "/" + siemensTask + "/nameplates/" + p.get(1) + "/url"), wang).path("message").asText(),
                "别的任务的型号对应的图");
        assertEquals("铭牌图片不存在", call(get(MY + "/" + siemensTask + "/nameplates/" + chat + "/url"), wang).path("message").asText(),
                "询盘附件采购看不到");
        assertEquals("询价任务不存在", call(get(MY + "/" + omronTask + "/nameplates"), wang).path("message").asText(),
                "不是自己的任务");

        JsonNode board = ok(call(get(BOARD + "/tasks/" + omronTask + "/nameplates"), admin));
        assertEquals(p.get(1), board.path("items").path(0).path("files").path(0).path("id").asLong());
        assertEquals(403, perform(get(BOARD + "/tasks/" + omronTask + "/nameplates"), token("it_lin")).getStatus(),
                "没有询价分配菜单");
    }

    @Test
    void update_andAppend_keepNameplatesSeparate() throws Exception {
        loginAsAdmin("it_np_admin");
        admin = token("it_np_admin");
        long id = parsedInquiry(OUTPUT);
        List<Long> p = plateIds(id);
        ok(call(json(post(INQ + "/" + id + "/confirm"), write(Map.of("rows", List.of(row("Siemens", "6ES7214-1AG40-0XB0", List.of(p.get(0))))))), admin));
        long chat = jdbc.queryForObject("select id from customer_inquiry_attachment where customer_inquiry_id = ? and kind = 1", Long.class, id);

        // 修改：删掉第 3 张铭牌，补传一张（不解析，先未对应）
        String added = upload("d.png", PNG, "image/png");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("source", 1);
        body.put("inquiryDate", jdbc.queryForObject("select inquiry_date from customer_inquiry where id = ?", String.class, id));
        body.put("quoteDeadline", jdbc.queryForObject("select quote_deadline from customer_inquiry where id = ?", String.class, id));
        body.put("keepAttachmentIds", List.of(chat, p.get(0), p.get(1)));
        body.put("nameplates", List.of(Map.of("fileKey", added, "fileName", "d.png")));
        ok(call(json(put(INQ + "/" + id), write(body)), admin));
        JsonNode detail = ok(call(get(INQ + "/" + id), admin));
        assertEquals(1, detail.path("attachments").size());
        assertEquals(3, detail.path("nameplates").size());
        assertEquals(itemId(id, 1), detail.path("nameplates").path(0).path("inquiryItemId").asLong(), "保留的铭牌对应关系不变");
        assertEquals("d.png", detail.path("nameplates").path(2).path("fileName").asText());
        assertEquals(0, detail.path("nameplates").path(2).path("inquiryItemId").asLong());

        // 追加型号：只对应这次追加的铭牌图片
        String extra = upload("e.png", PNG, "image/png");
        long appendId = ok(call(json(post(INQ + "/" + id + "/appends"),
                "{\"manual\":true" + refs("nameplates", List.of(extra)) + "}"), admin)).path("id").asLong();
        JsonNode draft = ok(call(get(INQ + "/" + id + "/appends/" + appendId + "/draft"), admin));
        assertEquals(1, draft.path("nameplates").size(), "追加确认页只列这次追加的铭牌");
        long extraId = draft.path("nameplates").path(0).path("id").asLong();
        ok(call(json(post(INQ + "/" + id + "/appends/" + appendId + "/confirm"), write(Map.of("rows", List.of(
                row("Omron", "E3Z-D61", List.of(extraId, p.get(1))))))), admin));
        assertEquals(itemId(id, 2), jdbc.queryForObject("select inquiry_item_id from customer_inquiry_attachment where id = ?", Long.class, extraId));
        assertEquals(0L, jdbc.queryForObject("select inquiry_item_id from customer_inquiry_attachment where id = ?", Long.class, p.get(1)),
                "不是这次追加的铭牌不能在追加确认时对应");
    }

    private static List<Long> ids(JsonNode arr) {
        List<Long> list = new ArrayList<>();
        arr.forEach(n -> list.add(n.asLong()));
        return list;
    }
}
