package com.zhul.erp.modules.system.storage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.framework.storage.FileStorage;
import com.zhul.erp.framework.storage.LocalFileStorage;
import com.zhul.erp.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 历史文件迁移与清理：预演不改数据；迁移后各表记录改为 OSS 新文件键（沿用原年月、按型号归档、公开地址改写），
 * 找不到的文件保持本地；重跑跳过已迁移的；清理只删核对过的本地文件，目标上缺失的保留，孤立文件只报告。
 * 目标存储用另一个本地目录模拟 OSS。
 */
class StorageMigrationIntegrationTest extends IntegrationTestBase {

    private static final int T = 99701;
    private static final String HEX = "0123456789abcdef0123456789abcdef";

    @Autowired
    private ObjectMapper objectMapper;

    @TempDir
    Path tmp;

    private LocalFileStorage local;
    private FakeOss oss;
    private long shipment;
    private long opportunityFile;
    private long receipt;
    private long media;
    private long config;

    /** 用本地目录模拟 OSS：存储方式为 OSS，公开地址为 CDN 域名 */
    static class FakeOss extends LocalFileStorage {
        FakeOss(Path dir) {
            super(dir.resolve("priv").toString(), dir.resolve("pub").toString(), "fake");
        }

        @Override
        public String code() {
            return FileStorage.OSS;
        }

        @Override
        public String publicUrl(String key) {
            return "https://cdn.test/" + key;
        }
    }

    @BeforeEach
    void seed() throws Exception {
        cleanup();
        local = new LocalFileStorage(tmp.resolve("local-priv").toString(), tmp.resolve("local-pub").toString(), "s");
        oss = new FakeOss(tmp.resolve("oss"));

        putLocal(false, "attachment/" + T + "/202609/" + HEX + ".jpg", photo());
        jdbc.update("insert into biz_attachment (tenant_id, owner_type, owner_id, kind, storage, file_key, url, file_name, file_size, "
                + "content_type, uploaded_by) values (?, 'SHIPMENT', 1, 1, 'LOCAL', ?, '', '发货.jpg', 1, 'image/jpeg', 0)",
                T, "attachment/" + T + "/202609/" + HEX + ".jpg");
        shipment = id("biz_attachment");

        putLocal(false, "opportunity/" + T + "/202608/" + HEX + ".csv", "model,qty\n6ES7,1".getBytes(StandardCharsets.UTF_8));
        jdbc.update("insert into opportunity_attachment (tenant_id, opportunity_id, storage, file_key, file_name, file_size, content_type) "
                + "values (?, 1, 'LOCAL', ?, 'bom.csv', 1, 'text/csv')", T, "opportunity/" + T + "/202608/" + HEX + ".csv");
        opportunityFile = id("opportunity_attachment");

        putLocal(false, "payment-slip/" + T + "/202607/" + HEX + ".png", png());
        jdbc.update("insert into payment_receipt (tenant_id, file_keys) values (?, ?)", T,
                "[{\"fileKey\":\"payment-slip/" + T + "/202607/" + HEX + ".png\",\"fileName\":\"水单.png\"},"
                        + "{\"fileKey\":\"payment-slip/" + T + "/202607/" + HEX.replace('0', 'f') + ".png\",\"fileName\":\"丢失.png\"}]");
        receipt = id("payment_receipt");

        jdbc.update("insert into product_brand (tenant_id, brand_name, status) values (0, 'ITMigBrand', 1)");
        long brand = jdbc.queryForObject("select id from product_brand where brand_name = 'ITMigBrand'", Long.class);
        jdbc.update("insert into product (tenant_id, brand_id, mpn_raw, mpn_normalized) values (0, ?, 'MIG-1', 'MIG1')", brand);
        long product = jdbc.queryForObject("select id from product where brand_id = ?", Long.class, brand);
        putLocal(true, "product/202609/" + HEX + ".png", png());
        jdbc.update("insert into product_media (tenant_id, product_id, media_type, file_url, storage_type, storage, file_key, file_size) "
                + "values (0, ?, 1, ?, 1, 'LOCAL', ?, 1)", product, "/uploads/product/202609/" + HEX + ".png", "product/202609/" + HEX + ".png");
        media = id("product_media");

        putLocal(true, "images/" + HEX + ".png", png());
        jdbc.update("insert into sys_config (tenant_id, config_key, config_value) values (?, 'it.mig.logo', ?)", T, "/uploads/images/" + HEX + ".png");
        config = id("sys_config");

        // 没有任何记录引用的本地文件
        putLocal(false, "attachment/" + T + "/202609/" + HEX.replace('1', 'e') + ".png", png());
    }

    @AfterEach
    void cleanup() {
        jdbc.update("delete from biz_attachment where tenant_id = ?", T);
        jdbc.update("delete from opportunity_attachment where tenant_id = ?", T);
        jdbc.update("delete from payment_receipt where tenant_id = ?", T);
        jdbc.update("delete from product_media where product_id in (select id from product where brand_id in "
                + "(select id from product_brand where brand_name = 'ITMigBrand'))");
        jdbc.update("delete from product where brand_id in (select id from product_brand where brand_name = 'ITMigBrand')");
        jdbc.update("delete from product_brand where brand_name = 'ITMigBrand'");
        jdbc.update("delete from sys_config where config_key = 'it.mig.logo'");
        jdbc.update("delete from storage_migration_log where old_key like ? or old_key like '%" + HEX + "%'", "%/" + T + "/%");
    }

    @Test
    void dryRun_changesNothing_butEstimatesSize() {
        Map<String, StorageMigration.Stat> report = migration(true).migrate();
        assertEquals("LOCAL", jdbc.queryForObject("select storage from biz_attachment where id = ?", String.class, shipment));
        assertEquals(0, count());
        StorageMigration.Stat s = report.get("biz_attachment");
        assertTrue(s.bytesAfter > 0 && s.bytesAfter < s.bytesBefore, "预演给出压缩后的估算大小");
        assertTrue(report.get("payment_receipt").problems.stream().anyMatch(p -> p.contains("找不到文件")));
    }

    @Test
    void run_movesEveryTable_rerunSkips_cleanupVerifies() throws Exception {
        migration(false).migrate();

        Map<String, Object> a = jdbc.queryForMap("select storage, file_key, file_size, content_type from biz_attachment where id = ?", shipment);
        assertEquals("OSS", a.get("storage"));
        assertTrue(a.get("file_key").toString().matches("tenant/" + T + "/shipment/202609/[0-9a-f]{32}\\.jpg"), a.toString());
        assertEquals(oss.size(false, a.get("file_key").toString()), ((Number) a.get("file_size")).longValue());

        String opp = jdbc.queryForObject("select file_key from opportunity_attachment where id = ?", String.class, opportunityFile);
        assertTrue(opp.startsWith("tenant/" + T + "/opportunity/202608/"), opp);

        JsonNode slips = objectMapper.readTree(jdbc.queryForObject("select file_keys from payment_receipt where id = ?", String.class, receipt));
        assertEquals("OSS", slips.get(0).path("storage").asText());
        assertTrue(slips.get(0).path("fileKey").asText().startsWith("tenant/" + T + "/finance/202607/"));
        assertEquals("水单.png", slips.get(0).path("fileName").asText());
        assertFalse(slips.get(1).has("storage"), "找不到的文件保持本地");

        Map<String, Object> m = jdbc.queryForMap("select storage, file_key, file_url from product_media where id = ?", media);
        assertEquals("OSS", m.get("storage"));
        assertTrue(m.get("file_key").toString().startsWith("shared/product/itmigbrand/mig-1/"), m.toString());
        assertEquals("https://cdn.test/" + m.get("file_key"), m.get("file_url"));
        assertTrue(oss.size(true, m.get("file_key").toString()) > 0, "商品图片放公开存储");

        String logo = jdbc.queryForObject("select config_value from sys_config where id = ?", String.class, config);
        assertTrue(logo.startsWith("https://cdn.test/tenant/" + T + "/system/"), logo);

        int moved = count();
        assertEquals(5, moved);
        migration(false).migrate();
        assertEquals(moved, count(), "重跑跳过已迁移的文件");

        // 清理：目标上缺失的那个保留本地文件；孤立文件只报告
        oss.delete(false, opp);
        Map<String, StorageMigration.Stat> report = migration(false).cleanup(tmp.resolve("local-priv"), tmp.resolve("local-pub"));
        assertEquals(-1, local.size(false, "attachment/" + T + "/202609/" + HEX + ".jpg"), "已核对的本地文件删除");
        assertTrue(local.size(false, "opportunity/" + T + "/202608/" + HEX + ".csv") > 0, "目标上缺失的保留");
        assertTrue(report.get("已迁移的本地文件").problems.stream().anyMatch(p -> p.contains("opportunity/")));
        StorageMigration.Stat orphans = report.get("没有记录引用的本地文件（未删除）");
        assertTrue(orphans.problems.stream().anyMatch(p -> p.contains(HEX.replace('1', 'e'))), orphans.problems.toString());
        assertTrue(orphans.problems.stream().noneMatch(p -> p.contains(HEX.replace('0', 'f'))), "仍被本地记录引用的不算孤立");
        assertTrue(local.size(false, "attachment/" + T + "/202609/" + HEX.replace('1', 'e') + ".png") > 0, "孤立文件不删除");
    }

    private StorageMigration migration(boolean dryRun) {
        return new StorageMigration(jdbc, local, oss, tmp, objectMapper, dryRun);
    }

    private int count() {
        return jdbc.queryForObject("select count(*) from storage_migration_log where old_key like '%" + HEX + "%'", Integer.class);
    }

    private long id(String table) {
        return jdbc.queryForObject("select max(id) from " + table, Long.class);
    }

    private void putLocal(boolean pub, String key, byte[] data) throws Exception {
        Path f = Files.write(tmp.resolve("src-" + System.nanoTime()), data);
        local.put(pub, key, f, "application/octet-stream");
    }

    private static byte[] png() throws Exception {
        BufferedImage img = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private static byte[] photo() throws Exception {
        BufferedImage img = new BufferedImage(3000, 2000, BufferedImage.TYPE_INT_RGB);
        Random rnd = new Random(3);
        for (int y = 0; y < 2000; y += 2) {
            for (int x = 0; x < 3000; x += 2) {
                img.setRGB(x, y, new Color(rnd.nextInt(256), 120, (x + y) % 256).getRGB());
            }
        }
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        ImageIO.write(img, "jpg", out);
        return out.toByteArray();
    }
}
