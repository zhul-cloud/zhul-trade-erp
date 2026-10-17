package com.zhul.erp.modules.media;

import com.zhul.erp.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 迁移 V1.3.3 的历史数据转换：构造拆分前的实拍任务（已完成、同型号重复的待拍摄、已有营销图的待拍摄、已跳过）
 * 与已有的发货、入库、回价附件，再执行一遍迁移文件里「历史实拍数据」之后的语句（语句可以重复执行），逐条核对。
 */
class MediaMigrationIntegrationTest extends IntegrationTestBase {

    private static final int T = 99702;

    private long doneTask;
    private long pendingDup1;
    private long pendingDup2;
    private long pendingHasMarketing;
    private long skippedTask;
    private long donePhoto;
    private long doneVideo;
    private long dupPhoto;
    private long shipmentPhoto;
    private long receiptPhoto;
    private long quotePhoto;
    private long receiptA;

    @BeforeEach
    void seed() {
        cleanup();
        // 采购入库单 A（含型号 A-1、C-1）
        jdbc.update("insert into purchase_receipt (tenant_id, gr_no, shipment_id, po_id, received_date, status) values (?, 'ITGR1', 0, 0, curdate(), 1)", T);
        receiptA = last("purchase_receipt");
        jdbc.update("insert into purchase_receipt_item (tenant_id, receipt_id, model, brand, shipped_qty, received_qty, qualified_qty, defective_qty) "
                + "values (?, ?, 'A-1', 'Siemens', 1, 1, 1, 0), (?, ?, 'C-1', 'Siemens', 1, 1, 1, 0)", T, receiptA, T, receiptA);

        doneTask = task("A-1", 2, "");
        donePhoto = att("SHOOT", doneTask, 1);
        doneVideo = att("SHOOT", doneTask, 2);
        pendingHasMarketing = task("A-1", 1, "");
        pendingDup1 = task("B-1", 1, "");
        pendingDup2 = task("B-1", 1, "");
        dupPhoto = att("SHOOT", pendingDup2, 1);
        skippedTask = task("D-1", 3, "客户不需要");

        // 供方发货单（型号 A-1、C-1）的图片；入库单 A 的验收照片；询价回价的图片
        jdbc.update("insert into supplier_shipment (tenant_id, sd_no, po_id, status) values (?, 'ITSD1', 0, 1)", T);
        long sd = last("supplier_shipment");
        jdbc.update("insert into supplier_shipment_item (tenant_id, shipment_id, model, brand, quantity) values (?, ?, 'A-1', 'Siemens', 1), "
                + "(?, ?, 'C-1', 'Siemens', 1)", T, sd, T, sd);
        shipmentPhoto = att("SHIPMENT", sd, 1);
        receiptPhoto = att("RECEIPT", receiptA, 1);
        jdbc.update("insert into sourcing_quote (tenant_id, brand, model) values (?, 'Siemens', 'Q-1')", T);
        quotePhoto = att("SOURCING_QUOTE", last("sourcing_quote"), 1);
    }

    @AfterEach
    void cleanup() {
        for (String table : List.of("media_item", "biz_attachment_model", "biz_attachment", "shoot_task", "purchase_receipt_item",
                "purchase_receipt", "supplier_shipment_item", "supplier_shipment", "sourcing_quote")) {
            jdbc.update("delete from " + table + " where tenant_id = ?", T);
        }
    }

    @Test
    void conversion_classifiesShootMedia_mergesTasks_indexesExistingAttachments() throws Exception {
        runConversion();

        assertEquals("9:1:siemens a-1", item(donePhoto), "已完成任务的实物图 → 营销实拍，可发客户");
        assertEquals("RECEIPT:" + receiptA, jdbc.queryForObject("select concat(owner_type, ':', owner_id) from biz_attachment where id = ?",
                String.class, doneVideo), "视频挂到来源入库单");
        assertEquals("7:1:siemens a-1", item(doneVideo), "视频 → 该型号的验货记录（只标任务型号）");

        assertEquals("3:已有营销图", task(pendingHasMarketing));
        assertEquals("1:", task(pendingDup1), "同型号保留最早的一条");
        assertEquals("3:合并到 " + pendingDup1, task(pendingDup2));
        assertEquals(pendingDup1, jdbc.queryForObject("select owner_id from biz_attachment where id = ?", Long.class, dupPhoto),
                "被合并任务的营销图挂到保留的任务");
        assertEquals("9:1:siemens b-1", item(dupPhoto));
        assertEquals("3:客户不需要", task(skippedTask), "已跳过的保持不变");
        assertEquals("2:", task(doneTask));

        assertEquals("6:0:siemens a-1,6:0:siemens c-1", item(shipmentPhoto), "供方发货标注发货单全部型号，仅内部");
        assertEquals("7:1:siemens a-1,7:1:siemens c-1", item(receiptPhoto), "验收照片标注入库单全部型号");
        assertEquals("4:0:siemens q-1", item(quotePhoto), "回价图片 → 供方实拍，仅内部");

        // 重复执行结果不变
        runConversion();
        assertEquals(8, jdbc.queryForObject("select count(*) from media_item where tenant_id = ? and deleted_at is null", Integer.class, T));
    }

    private void runConversion() throws Exception {
        String sql = new String(new ClassPathResource("db/migration/V1.3.3__media_library.sql").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        String part = sql.substring(sql.indexOf("-- ---------------------------------------------------------------- 历史实拍数据"));
        for (String stmt : Arrays.stream(part.split(";\\s*\\n")).map(String::trim).toList()) {
            String body = stmt.lines().filter(l -> !l.trim().startsWith("--")).reduce("", (a, b) -> a + "\n" + b).trim();
            if (!body.isEmpty()) {
                jdbc.execute(body);
            }
        }
    }

    private long task(String model, int status, String skipReason) {
        jdbc.update("insert into shoot_task (tenant_id, receipt_id, receipt_item_id, so_id, model, brand, category, asset_key, status, skip_reason) "
                + "values (?, ?, 0, 0, ?, 'Siemens', '', ?, ?, ?)", T, receiptA, model, "siemens " + model.toLowerCase(), status, skipReason);
        return last("shoot_task");
    }

    private long att(String ownerType, long ownerId, int kind) {
        jdbc.update("insert into biz_attachment (tenant_id, owner_type, owner_id, kind, storage, file_key, url, file_name, file_size, content_type, "
                + "uploaded_by) values (?, ?, ?, ?, 'LOCAL', 'k', '', 'f', 1, ?, 0)", T, ownerType, ownerId, kind, kind == 2 ? "video/mp4" : "image/png");
        return last("biz_attachment");
    }

    private long last(String table) {
        return jdbc.queryForObject("select max(id) from " + table + " where tenant_id = ?", Long.class, T);
    }

    private String task(long id) {
        return jdbc.queryForObject("select concat(status, ':', skip_reason) from shoot_task where id = ?", String.class, id);
    }

    private String item(long attachmentId) {
        return String.join(",", jdbc.queryForList("select concat(category, ':', can_send, ':', asset_key) from media_item "
                + "where source_table = 'biz_attachment' and source_id = ? and deleted_at is null order by asset_key", String.class, attachmentId));
    }
}
