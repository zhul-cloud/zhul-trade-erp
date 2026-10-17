package com.zhul.erp.modules.system.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.framework.storage.FileStorage;
import com.zhul.erp.framework.storage.FileStorageService;
import com.zhul.erp.framework.storage.ImageCompressor;
import com.zhul.erp.framework.storage.LocalFileStorage;
import com.zhul.erp.framework.storage.StorageArea;
import com.zhul.erp.framework.storage.StorageKeys;
import com.zhul.erp.framework.storage.UploadRules;
import com.zhul.erp.framework.storage.VideoTranscoder;
import com.zhul.erp.modules.attachment.service.AttachmentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 历史文件迁移与清理（规格「历史文件迁移」）：
 * <ul>
 *   <li>迁移：把本地存储的文件按新目录规则上传到目标存储（OSS），图片按上传规则压缩、视频标为转码中（下次启动时转码），
 *       每个文件上传并核对大小后才更新记录，并在 storage_migration_log 记下新旧文件键；失败或缺失的文件不影响其他文件，
 *       记录仍是本地，可以重复执行。预演只统计，不上传、不改记录。</li>
 *   <li>清理：按迁移记录逐个核对目标存储上的文件存在且大小一致后，才删除本地文件；没有任何记录引用的本地文件只列入报告。</li>
 * </ul>
 * 软删除的记录同样迁移（业务数据只软删除，文件仍要保留）。
 */
@Slf4j
public class StorageMigration {

    private static final int BATCH = 200;
    private static final Pattern MONTH = Pattern.compile("/(\\d{6})/");
    private static final int REPORT_LIMIT = 200;

    private final JdbcTemplate jdbc;
    private final LocalFileStorage local;
    private final FileStorage target;
    private final Path workDir;
    private final ObjectMapper objectMapper;
    private final boolean dryRun;
    private final Map<String, Stat> report = new LinkedHashMap<>();

    public StorageMigration(JdbcTemplate jdbc, LocalFileStorage local, FileStorage target, Path workDir,
                            ObjectMapper objectMapper, boolean dryRun) {
        this.jdbc = jdbc;
        this.local = local;
        this.target = target;
        this.workDir = workDir;
        this.objectMapper = objectMapper;
        this.dryRun = dryRun;
    }

    /** 每张表的统计：文件数、已迁移、找不到、失败、迁移前后总大小 */
    public static class Stat {
        public int total;
        public int moved;
        public int missing;
        public int failed;
        public long bytesBefore;
        public long bytesAfter;
        public final List<String> problems = new ArrayList<>();

        void problem(String s) {
            if (problems.size() < REPORT_LIMIT) {
                problems.add(s);
            }
        }
    }

    private record Moved(String key, long size, String ext) {
    }

    // ================================================================ 迁移

    public Map<String, Stat> migrate() {
        bizAttachments();
        simple("customer_inquiry_attachment", StorageArea.INQUIRY, true);
        simple("opportunity_attachment", StorageArea.OPPORTUNITY, true);
        simple("supplier_attachment", StorageArea.SUPPLIER, true);
        simple("purchase_order_attachment", StorageArea.PURCHASE, true);
        simple("document_template_version", StorageArea.DOCUMENT, false);
        simple("sourcing_import", StorageArea.SOURCING, false);
        paymentSlips();
        productMedia();
        siteImages();
        return report;
    }

    /** 业务附件：按所属单据类型放目录，实拍任务按任务的品牌与型号 */
    private void bizAttachments() {
        Stat stat = stat("biz_attachment");
        long cursor = 0;
        while (true) {
            List<Map<String, Object>> rows = jdbc.queryForList("select id, tenant_id, owner_type, owner_id, kind, file_key, file_name "
                    + "from biz_attachment where storage = 'LOCAL' and file_key <> '' and id > ? order by id limit " + BATCH, cursor);
            if (rows.isEmpty()) {
                return;
            }
            for (Map<String, Object> r : rows) {
                long id = num(r, "id");
                cursor = id;
                int tenant = (int) num(r, "tenant_id");
                String oldKey = str(r, "file_key");
                StorageArea area = AttachmentService.areaOf(str(r, "owner_type"));
                if (area == null) {
                    stat.total++;
                    stat.failed++;
                    stat.problem("未知的所属单据类型 " + r.get("owner_type") + "：" + oldKey);
                    continue;
                }
                Function<String, String> keyFor = ext -> area == StorageArea.MEDIA
                        ? shootKey(tenant, num(r, "owner_id"), ext, oldKey)
                        : StorageKeys.newKey(area, tenant, ext, month(oldKey));
                Moved m = move(stat, "biz_attachment", id, 0, false, oldKey, keyFor);
                if (m != null) {
                    boolean video = UploadRules.isVideo(m.ext());
                    jdbc.update("update biz_attachment set storage = 'OSS', file_key = ?, file_size = ?, content_type = ?, file_name = ?, "
                                    + "processing_status = ?, update_time = now() where id = ? and storage = 'LOCAL'",
                            m.key(), m.size(), UploadRules.contentTypeOf(m.ext()), renamed(str(r, "file_name"), oldKey, m.ext()),
                            video ? VideoTranscoder.PROCESSING : VideoTranscoder.NONE, id);
                }
            }
        }
    }

    private String shootKey(int tenant, long taskId, String ext, String oldKey) {
        List<Map<String, Object>> t = jdbc.queryForList("select brand, model from shoot_task where id = ? and tenant_id = ?", taskId, tenant);
        if (t.isEmpty()) {
            return StorageKeys.newKey(StorageArea.MEDIA, tenant, ext, month(oldKey));
        }
        return StorageKeys.modelKey(StorageArea.MEDIA, tenant, str(t.get(0), "brand"), str(t.get(0), "model"), ext);
    }

    /** 各模块的附件表：id、tenant_id、file_key、file_name，withMeta 时还有 file_size、content_type */
    private void simple(String table, StorageArea area, boolean withMeta) {
        Stat stat = stat(table);
        long cursor = 0;
        while (true) {
            List<Map<String, Object>> rows = jdbc.queryForList("select id, tenant_id, file_key, file_name from " + table
                    + " where storage = 'LOCAL' and file_key <> '' and file_key not like 'classpath:%' and id > ? order by id limit "
                    + BATCH, cursor);
            if (rows.isEmpty()) {
                return;
            }
            for (Map<String, Object> r : rows) {
                long id = num(r, "id");
                cursor = id;
                int tenant = (int) num(r, "tenant_id");
                String oldKey = str(r, "file_key");
                Moved m = move(stat, table, id, 0, false, oldKey, ext -> StorageKeys.newKey(area, tenant, ext, month(oldKey)));
                if (m == null) {
                    continue;
                }
                String name = renamed(str(r, "file_name"), oldKey, m.ext());
                if (withMeta) {
                    jdbc.update("update " + table + " set storage = 'OSS', file_key = ?, file_name = ?, file_size = ?, content_type = ?, "
                                    + "update_time = now() where id = ? and storage = 'LOCAL'",
                            m.key(), name, m.size(), UploadRules.contentTypeOf(m.ext()), id);
                } else {
                    jdbc.update("update " + table + " set storage = 'OSS', file_key = ?, file_name = ?, update_time = now() "
                            + "where id = ? and storage = 'LOCAL'", m.key(), name, id);
                }
            }
        }
    }

    /** 收款水单：file_keys 为数组，逐项迁移，每项记自己的存储方式 */
    private void paymentSlips() {
        Stat stat = stat("payment_receipt");
        long cursor = 0;
        while (true) {
            List<Map<String, Object>> rows = jdbc.queryForList("select id, tenant_id, file_keys from payment_receipt "
                    + "where file_keys is not null and file_keys like '[%' and id > ? order by id limit " + BATCH, cursor);
            if (rows.isEmpty()) {
                return;
            }
            for (Map<String, Object> r : rows) {
                long id = num(r, "id");
                cursor = id;
                int tenant = (int) num(r, "tenant_id");
                List<Map<String, Object>> items = readItems(str(r, "file_keys"));
                boolean changed = false;
                for (int i = 0; i < items.size(); i++) {
                    Map<String, Object> item = items.get(i);
                    Object storage = item.get("storage");
                    String oldKey = (String) item.get("fileKey");
                    if ((storage != null && !FileStorage.LOCAL.equals(storage)) || oldKey == null || oldKey.isEmpty()) {
                        continue;
                    }
                    Moved m = move(stat, "payment_receipt", id, i, false, oldKey,
                            ext -> StorageKeys.newKey(StorageArea.FINANCE, tenant, ext, month(oldKey)));
                    if (m != null) {
                        item.put("storage", FileStorage.OSS);
                        item.put("fileKey", m.key());
                        Object name = item.get("fileName");
                        item.put("fileName", renamed(name == null ? "" : name.toString(), oldKey, m.ext()));
                        changed = true;
                    }
                }
                if (changed) {
                    jdbc.update("update payment_receipt set file_keys = ?, update_time = now() where id = ?", writeItems(items), id);
                }
            }
        }
    }

    /** 共享商品库的图片与视频：公开存储，按商品的品牌与型号 */
    private void productMedia() {
        Stat stat = stat("product_media");
        long cursor = 0;
        while (true) {
            List<Map<String, Object>> rows = jdbc.queryForList("select m.id, m.media_type, m.file_key, p.mpn_raw, b.brand_name "
                    + "from product_media m join product p on p.id = m.product_id left join product_brand b on b.id = p.brand_id "
                    + "where m.storage_type = 1 and m.storage = 'LOCAL' and m.file_key <> '' and m.id > ? order by m.id limit "
                    + BATCH, cursor);
            if (rows.isEmpty()) {
                return;
            }
            for (Map<String, Object> r : rows) {
                long id = num(r, "id");
                cursor = id;
                String oldKey = str(r, "file_key");
                Moved m = move(stat, "product_media", id, 0, true, oldKey,
                        ext -> StorageKeys.modelKey(StorageArea.PRODUCT, 0, str(r, "brand_name"), str(r, "mpn_raw"), ext));
                if (m != null) {
                    boolean video = UploadRules.isVideo(m.ext());
                    jdbc.update("update product_media set storage = 'OSS', file_key = ?, file_url = ?, file_size = ?, processing_status = ?, "
                                    + "update_time = now() where id = ? and storage = 'LOCAL'",
                            m.key(), target.publicUrl(m.key()), m.size(), video ? VideoTranscoder.PROCESSING : VideoTranscoder.NONE, id);
                }
            }
        }
    }

    /** 站点外观图片：系统设置里保存的 /uploads/… 地址改写为公开存储的地址 */
    private void siteImages() {
        Stat stat = stat("sys_config");
        for (Map<String, Object> r : jdbc.queryForList("select id, tenant_id, config_value from sys_config where config_value like '/uploads/%'")) {
            long id = num(r, "id");
            int tenant = (int) num(r, "tenant_id");
            String value = str(r, "config_value");
            String oldKey = value.substring("/uploads/".length());
            Moved m = move(stat, "sys_config", id, 0, true, oldKey, ext -> StorageKeys.newKey(StorageArea.SYSTEM, tenant, ext));
            if (m != null) {
                jdbc.update("update sys_config set config_value = ?, update_time = now() where id = ? and config_value = ?",
                        target.publicUrl(m.key()), id, value);
            }
        }
    }

    /** 迁移一个文件：读本地 → 图片压缩 → 上传 → 核对大小 → 记迁移记录。预演只统计。失败返回 null */
    private Moved move(Stat stat, String table, long id, int index, boolean publicRead, String oldKey,
                       Function<String, String> keyFor) {
        stat.total++;
        long oldSize = local.size(publicRead, oldKey);
        if (oldSize < 0) {
            stat.missing++;
            stat.problem("找不到文件：" + table + "#" + id + " " + oldKey);
            return null;
        }
        stat.bytesBefore += oldSize;
        Path copy = null;
        Path compressed = null;
        try {
            String ext = UploadRules.extOf(oldKey);
            copy = Files.createTempFile(workDir, "mig-", "." + ext);
            try (InputStream in = local.open(publicRead, oldKey)) {
                Files.copy(in, copy, StandardCopyOption.REPLACE_EXISTING);
            }
            Path source = copy;
            String finalExt = ext;
            if (UploadRules.isImage(ext)) {
                ImageCompressor.Result r = ImageCompressor.compress(copy, ext, workDir);
                compressed = r.file();
                source = r.file();
                finalExt = r.ext();
            }
            long size = Files.size(source);
            if (dryRun) {
                stat.bytesAfter += size;
                return null;
            }
            String newKey = keyFor.apply(finalExt);
            target.put(publicRead, newKey, source, UploadRules.contentTypeOf(finalExt));
            long uploaded = target.size(publicRead, newKey);
            if (uploaded != size) {
                throw new IllegalStateException("上传后大小不一致：" + uploaded + " ≠ " + size);
            }
            jdbc.update("insert into storage_migration_log (source_table, record_id, item_index, public_read, old_key, new_key, new_size, status) "
                    + "values (?, ?, ?, ?, ?, ?, ?, 1)", table, id, index, publicRead ? 1 : 0, oldKey, newKey, size);
            stat.moved++;
            stat.bytesAfter += size;
            return new Moved(newKey, size, finalExt);
        } catch (IOException | RuntimeException e) {
            stat.failed++;
            stat.problem("迁移失败：" + table + "#" + id + " " + oldKey + "：" + e.getMessage());
            log.warn("迁移文件失败，{}#{} {}：{}", table, id, oldKey, e.toString());
            return null;
        } finally {
            FileStorageService.deleteTemp(copy);
            FileStorageService.deleteTemp(compressed);
        }
    }

    // ================================================================ 清理

    /**
     * 删除已迁移的本地文件：逐个核对目标存储上存在且大小一致后才删除；没有记录引用的本地文件只列入报告。
     *
     * @param privateRoot 本地私有目录
     * @param publicRoot  本地公开目录
     */
    public Map<String, Stat> cleanup(Path privateRoot, Path publicRoot) {
        Stat deleted = stat("已迁移的本地文件");
        Set<String> handled = new HashSet<>();
        for (Map<String, Object> r : jdbc.queryForList("select id, public_read, old_key, new_key, new_size, status from storage_migration_log")) {
            boolean pub = num(r, "public_read") == 1;
            String oldKey = str(r, "old_key");
            handled.add((pub ? "pub:" : "priv:") + oldKey);
            if (num(r, "status") != 1) {
                continue;
            }
            deleted.total++;
            long size = target.size(pub, str(r, "new_key"));
            if (size != num(r, "new_size")) {
                deleted.failed++;
                deleted.problem("目标存储上不存在或大小不一致，保留本地文件：" + oldKey);
                continue;
            }
            long localSize = local.size(pub, oldKey);
            deleted.bytesBefore += Math.max(localSize, 0);
            if (!dryRun) {
                if (localSize >= 0) {
                    local.delete(pub, oldKey);
                }
                jdbc.update("update storage_migration_log set status = 2, update_time = now() where id = ?", num(r, "id"));
            }
            deleted.moved++;
        }
        Set<String> referenced = localReferences();
        Stat orphans = stat("没有记录引用的本地文件（未删除）");
        listFiles(privateRoot, "priv:", handled, referenced, orphans);
        listFiles(publicRoot, "pub:", handled, referenced, orphans);
        return report;
    }

    /** 仍在本地存储、被记录引用的文件（迁移失败或未迁移的），清理时不能算作孤立文件 */
    private Set<String> localReferences() {
        Set<String> keys = new HashSet<>();
        for (String table : List.of("biz_attachment", "customer_inquiry_attachment", "opportunity_attachment", "supplier_attachment",
                "purchase_order_attachment", "document_template_version", "sourcing_import")) {
            jdbc.queryForList("select file_key from " + table + " where storage = 'LOCAL' and file_key <> ''", String.class)
                    .forEach(k -> keys.add("priv:" + k));
        }
        jdbc.queryForList("select file_key from product_media where storage = 'LOCAL' and file_key <> ''", String.class)
                .forEach(k -> keys.add("pub:" + k));
        jdbc.queryForList("select config_value from sys_config where config_value like '/uploads/%'", String.class)
                .forEach(v -> keys.add("pub:" + v.substring("/uploads/".length())));
        for (String json : jdbc.queryForList("select file_keys from payment_receipt where file_keys like '[%'", String.class)) {
            for (Map<String, Object> item : readItems(json)) {
                Object storage = item.get("storage");
                if (storage == null || FileStorage.LOCAL.equals(storage)) {
                    keys.add("priv:" + item.get("fileKey"));
                }
            }
        }
        return keys;
    }

    private void listFiles(Path root, String prefix, Set<String> handled, Set<String> referenced, Stat orphans) {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile).forEach(p -> {
                String key = prefix + root.relativize(p).toString().replace('\\', '/');
                if (!handled.contains(key) && !referenced.contains(key)) {
                    orphans.total++;
                    try {
                        orphans.bytesBefore += Files.size(p);
                    } catch (IOException e) {
                        // 统计大小失败不影响报告
                    }
                    orphans.problem(key);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("遍历本地目录失败：" + root, e);
        }
    }

    // ================================================================ 工具

    private Stat stat(String name) {
        return report.computeIfAbsent(name, k -> new Stat());
    }

    /** 旧文件键里的年月（如 attachment/1005/202610/…），没有时用当月 */
    static String month(String oldKey) {
        Matcher m = MONTH.matcher(oldKey);
        return m.find() ? m.group(1) : LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
    }

    private static String renamed(String fileName, String oldKey, String newExt) {
        if (fileName == null || fileName.isEmpty() || UploadRules.extOf(oldKey).equals(newExt)) {
            return fileName == null ? "" : fileName;
        }
        return UploadRules.withExt(fileName, newExt);
    }

    private List<Map<String, Object>> readItems(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() { });
        } catch (JsonProcessingException e) {
            return new ArrayList<>();
        }
    }

    private String writeItems(List<Map<String, Object>> items) {
        try {
            return objectMapper.writeValueAsString(items);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("写回水单附件失败", e);
        }
    }

    private static long num(Map<String, Object> r, String col) {
        Object v = r.get(col);
        return v == null ? 0 : ((Number) v).longValue();
    }

    private static String str(Map<String, Object> r, String col) {
        Object v = r.get(col);
        return v == null ? "" : v.toString();
    }
}
