package com.zhul.erp.modules.inquiry.sourcing;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.modules.inquiry.InquiryContractSupport;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.SheetVisibility;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** 询价包下载与导入询价结果（spec inquiry/sourcing-quote「下载询价包」「导入询价结果」） */
class SourcingExcelContractTest extends InquiryContractSupport {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private String admin;

    private long inquiryWithTask(String brand, String category, String... models) throws Exception {
        long c = customer("Wingrid Rocha", "Brazil");
        long id = ok(call(json(post(INQ), "{\"customerId\":" + c + ",\"source\":1,\"rawContent\":\"rfq\"}"), admin)).path("id").asLong();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String m : models) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("brand", brand);
            r.put("category", category);
            r.put("confirmedModel", m);
            r.put("quantity", 2);
            r.put("unit", "个");
            rows.add(r);
        }
        ok(call(json(post(INQ + "/" + id + "/confirm"), write(Map.of("rows", rows))), admin));
        return jdbc.queryForObject("select id from sourcing_task where customer_inquiry_id = ?", Long.class, id);
    }

    private void assign(long task, long who) throws Exception {
        ok(call(json(post(BOARD + "/assign"), "{\"taskIds\":[" + task + "],\"assigneeId\":" + who + "}"), admin));
    }

    private byte[] download(String path, String token, long... tasks) throws Exception {
        StringBuilder ids = new StringBuilder();
        for (long t : tasks) {
            ids.append(ids.isEmpty() ? "" : ",").append(t);
        }
        MockHttpServletResponse res = perform(get(path).param("taskIds", ids.toString()), token);
        assertEquals(200, res.getStatus(), res.getContentAsString());
        return res.getContentAsByteArray();
    }

    /** 在「1688」分区第一个型号行填价，在第二行填「约20」，在「闲鱼」分区第二个型号写无货 */
    private static byte[] fill(byte[] xlsx) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx)); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet s = wb.getSheet("②询价单");
            DataFormatter f = new DataFormatter();
            String section = "";
            int modelRowsSeen = 0;
            for (int r = 0; r <= s.getLastRowNum(); r++) {
                Row row = s.getRow(r);
                String a = row == null ? "" : f.formatCellValue(row.getCell(0));
                if (a.startsWith("【")) {
                    section = a;
                    modelRowsSeen = 0;
                    continue;
                }
                if (row == null || a.equals("型号") || a.startsWith("备注") || f.formatCellValue(row.getCell(5)).isEmpty()) {
                    continue;
                }
                modelRowsSeen++;
                if (section.startsWith("【1688】") && modelRowsSeen == 1) {
                    row.createCell(1).setCellValue("18.50");
                    row.createCell(2).setCellValue("全新原装");
                    row.createCell(3).setCellValue("现货");
                    row.createCell(4).setCellValue("三菱配件专营");
                } else if (section.startsWith("【1688】") && modelRowsSeen == 2) {
                    row.createCell(1).setCellValue("约20");
                    row.createCell(2).setCellValue("全新原装");
                    row.createCell(3).setCellValue("5天");
                    row.createCell(4).setCellValue("精工自动化");
                } else if (section.startsWith("【闲鱼】") && modelRowsSeen == 3) {
                    row.createCell(1).setCellValue("无货");
                }
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void downloadPackage_hasTemplateSheetsAndHiddenMeta() throws Exception {
        loginAsAdmin("it_xls_admin");
        admin = token("it_xls_admin");
        long task = inquiryWithTask("Mitsubishi", "其他", "MR-PWCNS4", "MR-PWCNS5");
        long other = inquiryWithTask("Omron", "传感器", "E3Z-D61");
        long unassigned = inquiryWithTask("Delta", "HMI", "DOP-110WS");
        assertTrue(download(BOARD + "/package", admin, unassigned).length > 0, "采购负责人可以先下载待分配任务的询价包");
        assign(task, PART_TIMER);
        assign(other, PART_TIMER);
        String wang = token("it_wang");

        MockHttpServletResponse res = perform(get(MY + "/package").param("taskIds", String.valueOf(task)), wang);
        assertTrue(res.getHeader("Content-Disposition").contains(java.net.URLEncoder.encode("询盘单A · Mitsubishi · 其他.xlsx", java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20")), res.getHeader("Content-Disposition"));
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(res.getContentAsByteArray()))) {
            assertEquals("①询盘单", wb.getSheetName(0));
            assertEquals("②询价单", wb.getSheetName(1));
            XSSFSheet meta = wb.getSheet("_meta");
            assertNotNull(meta);
            assertEquals(SheetVisibility.VERY_HIDDEN, wb.getSheetVisibility(wb.getSheetIndex(meta)));
            assertTrue(meta.getProtect(), "隐藏标识受保护");
            DataFormatter f = new DataFormatter();
            String inquiryText = f.formatCellValue(wb.getSheet("①询盘单").getRow(1).getCell(0));
            assertEquals("询盘单 A · Mitsubishi · 其他（2条）", inquiryText);
            assertTrue(wb.getSheet("②询价单").isColumnHidden(5));
            List<String> validated = wb.getSheet("②询价单").getDataValidations().stream()
                    .map(v -> String.join("/", v.getValidationConstraint().getExplicitListValues())).toList();
            assertTrue(validated.contains("全新原装/99新/翻新/二手/拆机件/国产替代/待确认"), "货况列只能从字典里选：" + validated);
            assertTrue(validated.stream().anyMatch(v -> v.startsWith("现货/1-2天")), "货期列只能从字典里选：" + validated);
        }

        byte[] zip = download(MY + "/package", wang, task, other);
        int entries = 0;
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(zip))) {
            while (z.getNextEntry() != null) {
                entries++;
            }
        }
        assertEquals(2, entries, "多个任务打包下载");
    }

    @Test
    void importResults_previewProblemsRejectsAndConfirm() throws Exception {
        loginAsAdmin("it_xls_admin");
        admin = token("it_xls_admin");
        long task = inquiryWithTask("Mitsubishi", "其他", "MR-PWCNS4", "MR-PWCNS5", "MR-BKCNS1");
        long notMine = inquiryWithTask("Omron", "传感器", "E3Z-D61");
        assign(task, PART_TIMER);
        assign(notMine, BUYER_LIN);
        String wang = token("it_wang");
        byte[] filled = fill(download(MY + "/package", wang, task));
        byte[] others = download(BOARD + "/package", admin, notMine);
        byte[] real;
        try (InputStream in = getClass().getResourceAsStream("/inquiry/dropsa-quoted.xlsx")) {
            real = in.readAllBytes();
        }

        JsonNode preview = ok(call(multipart(MY + "/import/preview")
                .file(new MockMultipartFile("files", "询盘单A · Mitsubishi · 其他.xlsx", XLSX, filled))
                .file(new MockMultipartFile("files", "询盘单A · OMRON · 传感器.xlsx", XLSX, others))
                .file(new MockMultipartFile("files", "Book1.xlsx", XLSX, real)), wang));
        JsonNode mine = preview.get(0);
        assertEquals(task, mine.path("taskId").asLong());
        assertEquals("王某", mine.path("assigneeName").asText());
        JsonNode rows = mine.path("rows");
        assertEquals(3, rows.size(), rows.toString());
        assertEquals(0, rows.get(0).path("problems").size());
        assertEquals("单价不是数字", rows.get(1).path("problems").get(0).asText());
        assertEquals("约20", rows.get(1).path("rawPrice").asText());
        assertEquals(1, rows.get(0).path("leadTime").asInt(), "现货");
        assertEquals(4, rows.get(1).path("leadTime").asInt(), "5天落在 3-5天");
        assertEquals("5天", rows.get(1).path("rawLeadTime").asText());
        assertTrue(rows.get(2).path("noStock").asBoolean());
        assertEquals("该询价任务未分配给你", preview.get(1).path("rejectReason").asText());
        assertEquals("找不到任务编号，可能不是从系统下载的询价包", preview.get(2).path("rejectReason").asText());

        List<Map<String, Object>> confirmRows = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            JsonNode r = rows.get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("itemId", r.path("itemId").asLong());
            m.put("channel", r.path("channel").asInt());
            m.put("shopName", r.path("shopName").asText());
            m.put("noStock", r.path("noStock").asBoolean());
            m.put("unitPrice", i == 1 ? "20" : (r.path("unitPrice").isNull() ? null : r.path("unitPrice").asText()));
            m.put("itemCondition", r.path("itemCondition").asInt());
            m.put("leadTime", r.path("leadTime").asInt());
            confirmRows.add(m);
        }
        Map<String, Object> file = new LinkedHashMap<>();
        file.put("taskId", task);
        file.put("fileKey", mine.path("fileKey").asText());
        file.put("fileName", "询盘单A · Mitsubishi · 其他.xlsx");
        file.put("rows", confirmRows);
        ok(call(json(post(MY + "/import/confirm"), write(Map.of("files", List.of(file)))), wang));

        assertEquals(3, jdbc.queryForObject("select count(*) from sourcing_quote where task_id = ? and entry_mode = 2 and status = 3", Integer.class, task),
                "兼职采购导入的回价进入待审核");
        assertEquals(PART_TIMER, jdbc.queryForObject("select on_behalf_of from sourcing_import where task_id = ?", Long.class, task));
        assertEquals(0, jdbc.queryForObject("select count(*) from inquiry_item where sourcing_task_id = ? and quote_status <> 1", Integer.class, task),
                "审核前不计入回价进度");

        // 采购负责人代兼职采购导入：询价人记为兼职采购，同样进入待审核
        Map<String, Object> proxyRow = new LinkedHashMap<>();
        long lastItem = jdbc.queryForObject("select id from inquiry_item where sourcing_task_id = ? and id not in "
                + "(select inquiry_item_id from sourcing_quote where task_id = ? and deleted_at is null)", Long.class, task, task);
        proxyRow.put("itemId", lastItem);
        proxyRow.put("channel", 1);
        proxyRow.put("noStock", false);
        proxyRow.put("unitPrice", "12.00");
        ok(call(json(post(BOARD + "/import/confirm"), write(Map.of("files", List.of(Map.of("taskId", task, "rows", List.of(proxyRow)))))), admin));
        assertEquals(PART_TIMER, jdbc.queryForObject("select quoted_by from sourcing_quote where inquiry_item_id = ?", Long.class, lastItem));
        assertEquals(3, jdbc.queryForObject("select status from sourcing_quote where inquiry_item_id = ?", Integer.class, lastItem));
        assertEquals(2, jdbc.queryForObject("select status from sourcing_task where id = ?", Integer.class, task), "审核前任务仍在询价中");

        // 采购负责人逐型号选推荐后通过：任务回齐
        List<Map<String, Object>> approve = new ArrayList<>();
        for (Long itemId : jdbc.queryForList("select id from inquiry_item where sourcing_task_id = ?", Long.class, task)) {
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("itemId", itemId);
            a.put("quotedBy", PART_TIMER);
            a.put("recommendedQuoteId", jdbc.queryForList("select id from sourcing_quote where inquiry_item_id = ? and no_stock = 0", Long.class, itemId)
                    .stream().findFirst().orElse(null));
            approve.add(a);
        }
        ok(call(json(post(BOARD + "/reviews/" + task + "/approve"), write(Map.of("items", approve))), admin));
        assertEquals(3, jdbc.queryForObject("select status from sourcing_task where id = ?", Integer.class, task));

        // 兼职工作台：只统计本人；本月回价 3 个型号（含负责人代导入的那条）
        JsonNode board = ok(call(get("/api/v1/inquiry/part-time-board"), wang));
        assertEquals(0, board.path("pendingTasks").asInt());
        assertEquals(3, board.path("monthQuotedItems").asInt(), board.toString());
        assertEquals(3, board.path("totalQuotedItems").asInt());
        assertEquals(1, board.path("monthNoStock").asInt());
        assertEquals(1, board.path("monthCompletedTasks").asInt());
        assertEquals(30, board.path("trend").size());
        assertEquals(3, board.path("trend").get(29).path("items").asInt(), "今天的柱子");
        assertEquals(403, perform(get("/api/v1/inquiry/part-time-board"), token("it_lin")).getStatus(), "没有兼职工作台菜单的采购不能访问");
    }
}
