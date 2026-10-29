package com.zhul.erp.modules.system;

import com.fasterxml.jackson.databind.JsonNode;
import cn.hutool.crypto.digest.BCrypt;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.system.service.UserService;
import com.zhul.erp.modules.tenant.dto.SaveTenantRequest;
import com.zhul.erp.modules.tenant.service.TenantService;
import com.zhul.erp.support.TenantContractSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.sun.net.httpserver.HttpServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * spec system/history-migration「执行迁移」「编码与新旧编号对照」「重复执行与回滚」「人员匹配」：
 * 干跑不写库；正式执行按历史日期编号、迁移标记、配对的询盘行、到账结汇与快照；重复执行不重复写入；
 * 回滚后可重新执行；租户账号不能执行；某阶段失败只回滚该阶段。
 */
class HistoryMigrationContractTest extends TenantContractSupport {

    private static final int TEST_PACKAGE_ID = 99000402;
    private static final String PLATFORM_USER = "it_hm_platform";
    private static final String API = "/api/v1/system/history-migrations";
    private static final String MODEL = "ITHM-MODEL-001";

    @Autowired
    private TenantService tenantService;

    @Autowired
    private UserService userService;

    @TempDir
    Path dir;

    private int tenantId;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(0);
        jdbc.update("insert into tenant_package (id, name, menu_ids, remark, status) values (?, '迁移测试套餐', '[]', '', 1)", TEST_PACKAGE_ID);
        SaveTenantRequest req = new SaveTenantRequest();
        req.setName("迁移测试_" + System.nanoTime());
        req.setPackageId(TEST_PACKAGE_ID);
        req.setContactName("测试联系人");
        req.setContactPhone("13800005555");
        req.setContactEmail("it-hm-" + System.nanoTime() + "@example.com");
        req.setExpireDate(LocalDate.now().plusYears(1));
        tenantId = tenantService.create(req).getTenant().getId();
        TenantContext.clear();
        loginAsAdmin(PLATFORM_USER);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("delete from account_local_auth where account_id in (select id from account where tenant_id = ?)", tenantId);
        for (String t : List.of("migration_id_map", "migration_batch", "sales_order_cost_snapshot", "fin_fx_settlement_allocation",
                "fin_fx_settlement", "payment_receipt", "fin_customer_refund", "fin_expense", "fin_other_income", "logistics_shipment",
                "supplier_shipment_item", "supplier_shipment", "purchase_order_log", "purchase_order_item", "purchase_order",
                "sales_order_item", "sales_order", "sourcing_quote", "sourcing_task_assignee", "sourcing_task", "inquiry_item",
                "customer_inquiry", "opportunity", "customer", "supplier", "document_sequence", "exchange_rate", "account", "user_basic")) {
            jdbc.update("delete from " + t + " where tenant_id = ?", tenantId);
        }
        jdbc.update("delete from product_candidate_source where tenant_id = ?", tenantId);
        jdbc.update("delete from product_candidate where tenant_id = 0 and mpn_normalized = 'ithmmodel001'");
        jdbc.update("delete from tenant where id = ?", tenantId);
        jdbc.update("delete from tenant_package where id = ?", TEST_PACKAGE_ID);
    }

    // ---------------------------------------------------------------- 样例清洗结果

    private void bundle(String orderCurrency, String orderRate) throws Exception {
        write("manifest", Map.of("version", "it-v1"));
        write("users", List.of(Map.of("name", "测试业务", "role", "sales"), Map.of("name", "测试采购", "role", "purchaser")));
        write("suppliers", List.of(Map.of("key", "S000001", "name", "王峰", "remark", "来源渠道：微信")));
        write("customers", List.of(map("key", "C00729", "name", "Diego Navarro", "contactName", "Diego Navarro", "country", "Mexico",
                "sourceChannel", 1, "aliLevel", "L2", "owner", "测试业务", "createdBy", "测试业务", "createTime", "2025-09-10 09:00:00")));
        write("opportunities", List.of(map("key", "SJ1", "customerKey", "C00729", "firstContactDate", "2025-09-10", "stage", "WON",
                "owner", "测试业务", "createdBy", "测试业务", "createTime", "2025-09-10 09:00:00", "sourceChannel", 1, "reachedValid", true)));
        Map<String, Object> line = map("lineNo", 1, "brand", "Siemens", "model", MODEL, "quantity", 2, "confidence", 1);
        write("inquiries", List.of(map("key", "XP1", "customerKey", "C00729", "opportunityKey", "SJ1", "inquiryDate", "2025-10-10",
                "urgent", 0, "level", 2, "status", 7, "customerType", 1, "owner", "测试业务", "createdBy", "测试业务",
                "createTime", "2025-10-10 10:00:00", "lines", List.of(line))));
        write("sourcing", List.of(map("key", "XP1#测试采购", "inquiryKey", "XP1", "purchaser", "测试采购", "createTime", "2025-10-11 10:00:00",
                "quotes", List.of(map("key", "XJ1#1", "lineNo", 1, "model", MODEL, "unitPrice", "1350", "noStock", false, "taxIncluded", 0,
                        "itemCondition", 1, "leadTime", 1, "channel", 1, "shopName", "某店", "quotedAt", "2025-10-11 10:00:00")))));
        Map<String, Object> receipt = map("key", "JH1", "currency", "USD", "amount", "430.00", "platformFee", "12.90", "netAmount", "417.10",
                "exchangeRate", "6.69", "amountCny", "2876.70", "netAmountCny", "2790.40", "settled", false, "platformOrderNo", "320100000001000011",
                "receiptDate", "2025-10-23", "operator", "测试业务", "createTime", "2025-10-23 14:20:11",
                "settlement", map("date", "2025-10-23", "amount", "417.10", "exchangeRate", "6.69", "amountCny", "2790.40"));
        Map<String, Object> order = map("key", "SO1", "customerKey", "C00729", "salesDate", "2025-10-22", "customerType", 2, "stockType", 1,
                "owner", "测试业务", "purchaser", "测试采购", "currency", orderCurrency, "exchangeRate", orderRate, "totalAmount", "430.00",
                "paymentMethod", "ALIBABA_TA", "progress", "SHIPPED", "rawStatus", "已发货", "createdBy", "测试业务", "createTime", "2025-10-22 00:00:00",
                "lines", List.of(map("lineNo", 1, "brand", "Siemens", "model", MODEL, "quantity", 2, "unitPrice", "215.00", "amount", "430.00",
                        "inquiryLine", map("inquiryKey", "XP1", "lineNo", 1))),
                "receipts", List.of(receipt),
                "snapshot", map("revenue", "2876.70", "cost", "1807.75", "grossProfit", "1068.95", "refund", "0", "purchase", "1721.45",
                        "freight", "0", "surcharge", "0", "serviceFee", "86.30", "settled", false, "accountingMonth", "2025-10", "sourceCodes", List.of("HS1")));
        write("orders", List.of(order));
        write("purchases", List.of(map("key", "PO1", "soKey", "SO1", "purchaser", "测试采购", "channel", 4, "supplierKey", "S000001",
                "orderDate", "2025-10-22", "totalAmount", "1721.45", "paidAmount", "1721.45", "remark", "江工代付", "createTime", "2025-10-22 10:00:00",
                "lines", List.of(map("soLineNo", 1, "model", MODEL, "brand", "Siemens", "quantity", 2, "unitPrice", "860.73", "amount", "1721.45")))));
        write("shipments", List.of(map("key", "FH1", "poKey", "PO1", "soKey", "SO1", "shipDate", "2025-10-25",
                "supplierTracks", List.of(map("carrier", "顺丰", "trackingNo", "SF1500000858457")),
                "international", map("carrier", "DHL", "waybillNo", "6800001110", "freight", "392.00", "raw", "6800001110 卡塔尔 2KG，运费：392元，DHL"))));
        write("finance", map("expenses", List.of(map("key", "2025-07#RENT", "category", "RENT", "name", "2025-07 房租", "date", "2025-07-31", "amount", "1100.00")),
                "otherIncomes", List.of(), "refunds", List.of()));
        write("media", List.of());
        reports("2876.70");
    }

    /** 旧报表数字（pack.js 生成的 reports.json）：2025-10 的销售收入可以故意写错 */
    private void reports(String octRevenue) throws Exception {
        write("reports", map("operating", List.of(
                map("month", "2025-07", "revenue", "0", "cost", "0", "refund", null, "otherIncome", null, "opex", "1100.00"),
                map("month", "2025-10", "revenue", octRevenue, "cost", "1807.75", "refund", "0", "otherIncome", "0", "opex", "0"))));
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private void write(String name, Object value) throws Exception {
        Files.writeString(dir.resolve(name + ".json"), write(value));
    }

    private JsonNode run(boolean dryRun, String token) throws Exception {
        return call(json(post(API), write(map("tenantId", tenantId, "bundleDir", dir.toString(), "dryRun", dryRun))), token);
    }

    /** 执行在后台运行：提交后等批次结束 */
    private JsonNode runDone(boolean dryRun) throws Exception {
        JsonNode batch = ok(run(dryRun, token(PLATFORM_USER, 0)));
        assertEquals(1, batch.path("status").asInt(), "提交后立即返回运行中的批次");
        return await(batch.path("id").asLong());
    }

    private JsonNode await(long batchId) throws Exception {
        for (int i = 0; i < 600; i++) {
            JsonNode b = ok(call(get(API + "/" + batchId), token(PLATFORM_USER, 0)));
            if (b.path("status").asInt() != 1 && !b.path("rollingBack").asBoolean()) {
                return b;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("迁移批次 60 秒内没有结束：" + batchId);
    }

    private int count(String table, String where) {
        return jdbc.queryForObject("select count(*) from " + table + " where tenant_id = ? and deleted_at is null" + (where.isEmpty() ? "" : " and " + where),
                Integer.class, tenantId);
    }

    private int created(JsonNode batch, String stage, String kind) {
        return batch.path("stageStats").path(stage).path(kind).path("新建").asInt();
    }

    @Test
    void dryRun_run_rerun_rollback_rerun() throws Exception {
        bundle("USD", "6.69");
        String platform = token(PLATFORM_USER, 0);

        // 干跑：报告将新建的数量，库里没有任何变化
        JsonNode dry = runDone(true);
        assertEquals(2, dry.path("status").asInt(), dry.toString());
        assertEquals(1, created(dry, "主数据", "客户"));
        assertEquals(1, created(dry, "订单与下游", "销售订单"));
        assertEquals(0, count("customer", ""));
        assertEquals(0, jdbc.queryForObject("select count(*) from document_sequence where tenant_id = ?", Integer.class, tenantId), "干跑不推进编号");

        // 正式执行
        JsonNode first = runDone(false);
        assertEquals(2, first.path("status").asInt(), first.toString());
        assertEquals(created(dry, "订单与下游", "销售订单"), created(first, "订单与下游", "销售订单"), "干跑与正式执行数量一致");
        assertEquals("OPP20250910001", jdbc.queryForObject("select opportunity_code from opportunity where tenant_id = ?", String.class, tenantId));
        assertEquals("IQ20251010001", jdbc.queryForObject("select inquiry_code from customer_inquiry where tenant_id = ?", String.class, tenantId));
        assertEquals("SO20251022001", jdbc.queryForObject("select so_no from sales_order where tenant_id = ?", String.class, tenantId));
        assertEquals(1, count("sales_order", "migrated = 1"));
        // 切换日之前的单据一律完结：已报价的询盘记为未成交，订单进度已完成、收款已结清，到账已结汇
        assertEquals(1, count("customer_inquiry", "migrated = 1 and status = 9"));
        assertEquals(1, count("sales_order", "progress_code = 'COMPLETED' and receipt_status = 4 and completed_at is not null and remark like '%旧系统状态：已发货%'"));
        assertEquals(1, count("payment_receipt", "settle_status = 3 and settled_amount = net_amount"));
        assertEquals(1, count("sales_order_cost_snapshot", "settled = 1"), "核算单一律记为已结算");
        assertEquals(1, count("purchase_receipt", "status = 1 and received_date = '2025-10-25'"), "采购单视为已全部入库，入库日期取发货日期");
        assertEquals(1, count("purchase_receipt_item", "received_qty = 2 and qualified_qty = 2"));
        long item = jdbc.queryForObject("select id from inquiry_item where tenant_id = ?", Long.class, tenantId);
        assertEquals(item, jdbc.queryForObject("select inquiry_item_id from sales_order_item where tenant_id = ?", Long.class, tenantId), "订单行关联询盘行");
        assertEquals(1, count("sourcing_quote", "status = 2 and entry_mode = 2 and unit_price = 1350"));
        assertEquals(0, new java.math.BigDecimal("2790.40").compareTo(jdbc.queryForObject(
                "select amount_cny from fin_fx_settlement where tenant_id = ? and migrated = 1", java.math.BigDecimal.class, tenantId)));
        assertEquals(1, count("sales_order_cost_snapshot", "revenue = 2876.70"));
        assertEquals(1, count("fin_expense", "category = 'RENT' and occur_date = '2025-07-31'"));
        assertEquals(1, count("logistics_shipment", "waybill_no = '6800001110' and freight = 392.00"));
        // 正式执行成功后自动与旧报表核对；故意写错一个数字能报出不一致
        assertEquals(0, first.path("verifyResult").path("summary").path("inconsistent").asInt(), first.path("verifyResult").toString());
        assertEquals(10, first.path("verifyResult").path("summary").path("total").asInt());
        reports("2800.00");
        JsonNode wrong = ok(call(json(post(API + "/verify"), write(map("tenantId", tenantId, "bundleDir", dir.toString()))), platform));
        assertEquals(1, wrong.path("summary").path("inconsistent").asInt(), wrong.toString());
        reports("2876.70");
        assertEquals(2, jdbc.queryForObject("select count(*) from user_basic where tenant_id = ? and status = 0", Integer.class, tenantId), "新建的用户停用");
        // 新建的人员带登录账号：管理员重置密码后可以用新密码登录
        Integer buyer = jdbc.queryForObject("select id from user_basic where tenant_id = ? and name = '测试采购'", Integer.class, tenantId);
        assertEquals(1, jdbc.queryForObject("select count(*) from account a join account_local_auth l on l.account_id = a.id where a.user_id = ? and a.status = 1",
                Integer.class, buyer));
        userService.resetPassword(buyer, "Init@2026");
        assertTrue(BCrypt.checkpw("Init@2026", jdbc.queryForObject("select l.password from account a join account_local_auth l on l.account_id = a.id where a.user_id = ?",
                String.class, buyer)));
        assertTrue(jdbc.queryForObject("select count(*) from product_candidate c join product_candidate_source s on s.candidate_id = c.id "
                + "where s.tenant_id = ? and c.mpn_normalized = 'ithmmodel001'", Integer.class, tenantId) >= 1, "型号进入候选池");
        assertEquals(3, jdbc.queryForObject("select c.level from product_candidate c where c.tenant_id = 0 and c.mpn_normalized = 'ithmmodel001' "
                + "and c.deleted_at is null", Integer.class), "成交的型号可信度为已成交");

        // 重复执行：已迁移的都跳过
        JsonNode again = runDone(false);
        assertEquals(0, created(again, "主数据", "客户"));
        assertEquals(1, count("customer", ""));
        assertEquals(1, count("sales_order", ""));

        // 回滚后可以重新执行
        ok(call(post(API + "/" + first.path("id").asLong() + "/rollback"), platform));
        JsonNode rolled = await(first.path("id").asLong());
        assertEquals(4, rolled.path("status").asInt());
        assertEquals(0, count("sales_order", ""));
        assertEquals(0, count("customer_inquiry", ""));
        assertEquals(0, count("purchase_receipt", ""));
        assertEquals(0, jdbc.queryForObject("select count(*) from product_candidate_source where tenant_id = ? and deleted_at is null",
                Integer.class, tenantId), "回滚后候选来源移除");
        assertEquals(0, jdbc.queryForObject("select count(*) from product_candidate where tenant_id = 0 and mpn_normalized = 'ithmmodel001' "
                + "and deleted_at is null", Integer.class), "没有来源的待审核候选移除");
        JsonNode rerun = runDone(false);
        assertEquals(1, created(rerun, "订单与下游", "销售订单"));
        assertEquals(1, count("sales_order", ""));
    }

    /** 生产方式：上传 zip 迁移包，图片按清单里的下载链接取（这里用本机临时服务代替开发 OSS 的临时链接） */
    @Test
    void uploadedBundle_downloadsImagesByUrl() throws Exception {
        bundle("USD", "6.69");
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "png", png);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/img.png", ex -> {
            ex.sendResponseHeaders(200, png.size());
            try (var out = ex.getResponseBody()) {
                out.write(png.toByteArray());
            }
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/img.png";
            write("media", List.of(
                    map("category", 6, "sourceKey", "FH1", "token", "T1", "file", "t1.png", "url", url, "brand", "Siemens", "model", MODEL),
                    map("category", 6, "sourceKey", "FH1", "token", "T2", "file", "t2.png", "url", url.replace("img.png", "missing.png"), "brand", "Siemens", "model", MODEL)));
            String platform = token(PLATFORM_USER, 0);
            JsonNode up = ok(call(multipart(API + "/bundles").file(new MockMultipartFile("file", "bundle.zip", "application/zip", zip(Map.of()))), platform));
            assertEquals(1, up.path("counts").path("customers").asInt());
            JsonNode batch = ok(call(json(post(API), write(map("tenantId", tenantId, "bundleId", up.path("id").asText(), "dryRun", false))), platform));
            // 同一时间只运行一个迁移任务
            JsonNode busy = fail(call(json(post(API), write(map("tenantId", tenantId, "bundleId", up.path("id").asText(), "dryRun", true))), platform));
            assertTrue(busy.path("message").asText().contains("已有迁移任务在运行"), busy.toString());
            JsonNode done = await(batch.path("id").asLong());
            assertEquals(2, done.path("status").asInt(), done.toString());
            assertEquals(1, created(done, "候选商品与素材", "供方发货"), done.toString());
            assertEquals(1, jdbc.queryForObject("select count(*) from biz_attachment where tenant_id = ? and deleted_at is null", Integer.class, tenantId));
            assertEquals(1, done.path("stageStats").path("候选商品与素材").path("素材图片（取不到文件）").path("失败").asInt(), "下载失败计入失败，不中断");
        } finally {
            server.stop(0);
            jdbc.update("delete from media_item where tenant_id = ?", tenantId);
            jdbc.update("delete from biz_attachment where tenant_id = ?", tenantId);
        }
        // zip 里有越界路径或不认识的文件时拒绝
        fail(call(multipart(API + "/bundles").file(new MockMultipartFile("file", "bad.zip", "application/zip",
                zip(Map.of("../evil.json", "[]")))), token(PLATFORM_USER, 0)));
    }

    /** 图片链接过期或服务器访问不了：试下载第一张就失败，不逐张空等；前面的阶段保留 */
    @Test
    void imageProbeFails_stopsBeforeArchiving() throws Exception {
        bundle("USD", "6.69");
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            ex.sendResponseHeaders(403, -1);
            ex.close();
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/img.png";
            write("media", List.of(
                    map("category", 6, "sourceKey", "FH1", "token", "T1", "file", "t1.png", "url", url, "brand", "Siemens", "model", MODEL)));
            JsonNode res = runDone(false);
            assertEquals(3, res.path("status").asInt(), res.toString());
            assertTrue(res.path("errorText").asText().contains("403"), res.toString());
            assertEquals(1, count("sales_order", ""), "前面的阶段保留");
            assertEquals(0, count("biz_attachment", ""));
        } finally {
            server.stop(0);
        }
    }

    /** 把样例清洗结果目录打成 zip，extra 追加额外条目 */
    private byte[] zip(Map<String, String> extra) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(bytes); var files = Files.list(dir)) {
            for (Path p : files.toList()) {
                z.putNextEntry(new ZipEntry(p.getFileName().toString()));
                z.write(Files.readAllBytes(p));
                z.closeEntry();
            }
            for (Map.Entry<String, String> e : extra.entrySet()) {
                z.putNextEntry(new ZipEntry(e.getKey()));
                z.write(e.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                z.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    @Test
    void tenantAccount_rejected() throws Exception {
        bundle("USD", "6.69");
        fail(run(true, token(PLATFORM_USER, tenantId)));
    }

    @Test
    void failedStage_rollsBackOnlyThatStage_andStops() throws Exception {
        bundle("EUR", null); // 订单没有汇率且租户没有设置 EUR 汇率 → 订单阶段失败
        JsonNode res = runDone(false);
        assertEquals(3, res.path("status").asInt(), res.toString());
        assertTrue(res.path("errorText").asText().contains("EUR"));
        assertEquals(1, count("customer_inquiry", ""), "前面的阶段保留");
        assertEquals(0, count("sales_order", ""), "失败的阶段回滚");
        assertEquals(0, count("fin_expense", ""), "后面的阶段不执行");
    }
}
