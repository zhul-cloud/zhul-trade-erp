package com.zhul.erp.framework.storage;

import com.zhul.erp.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 存储配置、文件键规则、本地存储与签名链接 */
class StorageFoundationTest {

    private static final String MONTH = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));

    @TempDir
    Path tmp;

    // ---------- 配置 ----------

    @Test
    void ossSelected_missingPrivateBucket_failsWithItemName() {
        StorageProperties p = new StorageProperties();
        p.setType("oss");
        p.getOss().setEndpoint("https://oss-cn-hangzhou.aliyuncs.com");
        p.getOss().setAccessKeyId("id");
        p.getOss().setAccessKeySecret("secret");
        p.getOss().setPublicBucket("pub");
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> new FileStorages(p, local(), null));
        assertThat(e.getMessage()).contains("OSS_PRIVATE_BUCKET").doesNotContain("OSS_PUBLIC_BUCKET");
    }

    @Test
    void localSelected_needsNoOssConfig_readingOssRecordExplains() {
        FileStorages s = new FileStorages(new StorageProperties(), local(), null);
        assertThat(s.current().code()).isEqualTo(FileStorage.LOCAL);
        assertThat(s.of(null).code()).isEqualTo(FileStorage.LOCAL);
        assertThat(assertThrows(BizException.class, () -> s.of("OSS")).getMessage()).contains("没有配置 OSS");
    }

    @Test
    void unknownType_rejected() {
        StorageProperties p = new StorageProperties();
        p.setType("s3");
        assertThat(assertThrows(IllegalStateException.class, () -> new FileStorages(p, local(), null)).getMessage())
                .contains("local 或 oss");
    }

    @Test
    void propertiesToString_hidesSecrets() {
        StorageProperties p = new StorageProperties();
        p.getOss().setAccessKeyId("AKID-VISIBLE?");
        p.getOss().setAccessKeySecret("TOP-SECRET");
        assertThat(p.toString()).doesNotContain("TOP-SECRET").doesNotContain("AKID-VISIBLE?");
    }

    // ---------- 文件键 ----------

    @Test
    void keys_followDirectoryTable() {
        assertThat(StorageKeys.newKey(StorageArea.FINANCE, 1005, "png"))
                .matches("tenant/1005/finance/" + MONTH + "/[0-9a-f]{32}\\.png");
        assertThat(StorageKeys.newKey(StorageArea.INQUIRY, 1005, "pdf")).startsWith("tenant/1005/inquiry/" + MONTH + "/");
        assertThat(StorageKeys.newKey(StorageArea.SYSTEM, 0, "jpg")).matches("tenant/0/system/[0-9a-f]{32}\\.jpg");
        assertThat(StorageKeys.newKey(StorageArea.PACKING, 1005, "jpg")).matches("tenant/1005/packing/" + MONTH + "/[0-9a-f]{32}\\.jpg");
        assertThat(StorageKeys.modelKey(StorageArea.MEDIA, 1005, "Siemens", "6ES7214-1AG40-0XB0", "jpg"))
                .matches("tenant/1005/media/siemens/6es7214-1ag40-0xb0/[0-9a-f]{32}\\.jpg");
        assertThat(StorageKeys.modelKey(StorageArea.PRODUCT, 0, "Allen-Bradley", "1756-L71", "webp"))
                .startsWith("shared/product/allen-bradley/1756-l71/");
        assertThat(StorageKeys.newKey(StorageArea.MEDIA, 1, "jpg")).startsWith("tenant/1/media/unsorted/" + MONTH + "/");
        assertThrows(IllegalArgumentException.class, () -> StorageKeys.newKey(StorageArea.PRODUCT, 1, "jpg"));
    }

    @Test
    void slug_normalizesCaseSpacesAndSlashes() {
        assertThat(StorageKeys.slug("  Mitsubishi Electric ")).isEqualTo("mitsubishi-electric");
        assertThat(StorageKeys.slug("FX3U-32MR/ES-A")).isEqualTo("fx3u-32mr-es-a");
        assertThat(StorageKeys.slug("A//B..C")).isEqualTo("a-b-c");
        assertThat(StorageKeys.slug("../..")).isEqualTo("unknown");
        assertThat(StorageKeys.slug(null)).isEqualTo("unknown");
    }

    @Test
    void owned_checksTenantAreaAndLegacyKeys() {
        String key = StorageKeys.newKey(StorageArea.INQUIRY, 1005, "pdf");
        assertThat(StorageKeys.owned(StorageArea.INQUIRY, key, 1005)).isTrue();
        assertThat(StorageKeys.owned(StorageArea.INQUIRY, key, 1006)).isFalse();
        assertThat(StorageKeys.owned(StorageArea.OPPORTUNITY, key, 1005)).isFalse();
        String legacy = "customer-inquiry/1005/202610/ff1f37ec136249218638b4ce7222c062.jpg";
        assertThat(StorageKeys.owned(StorageArea.INQUIRY, legacy, 1005)).isTrue();
        assertThat(StorageKeys.owned(StorageArea.INQUIRY, legacy, 1000)).isFalse();
        assertThat(StorageKeys.owned(StorageArea.INQUIRY, "tenant/1005/inquiry/202610/../x.pdf", 1005)).isFalse();
        assertThat(StorageKeys.owned(StorageArea.MEDIA,
                StorageKeys.modelKey(StorageArea.MEDIA, 7, "Siemens", "6ES7", "jpg"), 7)).isTrue();
    }

    // ---------- 本地存储与签名链接 ----------

    @Test
    void local_putOpenSizeDelete_publicAndPrivateSeparated() throws Exception {
        LocalFileStorage s = local();
        Path src = Files.writeString(tmp.resolve("src.pdf"), "%PDF-1.7 hello");
        s.put(false, "tenant/1/finance/202610/a.pdf", src, "application/pdf");
        assertThat(s.size(false, "tenant/1/finance/202610/a.pdf")).isEqualTo(14);
        assertThat(s.size(true, "tenant/1/finance/202610/a.pdf")).isEqualTo(-1);
        try (InputStream in = s.open(false, "tenant/1/finance/202610/a.pdf")) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("%PDF-1.7 hello");
        }
        s.put(true, "shared/product/x/y/b.jpg", src, "image/jpeg");
        assertThat(s.publicUrl("shared/product/x/y/b.jpg")).isEqualTo("/uploads/shared/product/x/y/b.jpg");
        assertThat(Files.exists(tmp.resolve("pub/shared/product/x/y/b.jpg"))).isTrue();
        s.delete(false, "tenant/1/finance/202610/a.pdf");
        assertThat(s.size(false, "tenant/1/finance/202610/a.pdf")).isEqualTo(-1);
        assertThrows(BizException.class, () -> s.open(false, "../outside.pdf"));
    }

    @Test
    void localSignedUrl_valid_tampered_expired() {
        LocalFileStorage s = local();
        Map<String, String> q = query(s.signedUrl("tenant/1/finance/202610/a.pdf", "水单 1.pdf",
                Instant.now().plusSeconds(600), true));
        long exp = Long.parseLong(q.get("e"));
        assertThat(q.get("k")).isEqualTo("tenant/1/finance/202610/a.pdf");
        assertThat(q.get("n")).isEqualTo("水单 1.pdf");
        assertThat(s.verify(q.get("k"), q.get("n"), q.get("d"), exp, q.get("s"))).isTrue();
        // 换文件、换文件名、改成预览、延长有效期都会让签名失效
        assertThat(s.verify("tenant/2/finance/202610/a.pdf", q.get("n"), q.get("d"), exp, q.get("s"))).isFalse();
        assertThat(s.verify(q.get("k"), "x.pdf", q.get("d"), exp, q.get("s"))).isFalse();
        assertThat(s.verify(q.get("k"), q.get("n"), "0", exp, q.get("s"))).isFalse();
        assertThat(s.verify(q.get("k"), q.get("n"), q.get("d"), exp + 3600, q.get("s"))).isFalse();

        Map<String, String> old = query(s.signedUrl("k.pdf", "a.pdf", Instant.now().minusSeconds(1), false));
        assertThat(s.verify(old.get("k"), old.get("n"), old.get("d"), Long.parseLong(old.get("e")), old.get("s"))).isFalse();
    }

    @Test
    void localSignedUrl_otherSecretRejected() {
        Map<String, String> q = query(local().signedUrl("k.pdf", "a.pdf", Instant.now().plusSeconds(60), false));
        LocalFileStorage other = new LocalFileStorage(tmp.resolve("priv").toString(), tmp.resolve("pub").toString(), "another");
        assertThat(other.verify(q.get("k"), q.get("n"), q.get("d"), Long.parseLong(q.get("e")), q.get("s"))).isFalse();
    }

    private LocalFileStorage local() {
        return new LocalFileStorage(tmp.resolve("priv").toString(), tmp.resolve("pub").toString(), "test-secret");
    }

    private static Map<String, String> query(String url) {
        Map<String, String> m = new HashMap<>();
        for (String kv : URI.create(url).getRawQuery().split("&")) {
            String[] p = kv.split("=", 2);
            m.put(p[0], URLDecoder.decode(p[1], StandardCharsets.UTF_8));
        }
        return m;
    }
}
