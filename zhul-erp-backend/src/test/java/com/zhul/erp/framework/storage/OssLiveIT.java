package com.zhul.erp.framework.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实 OSS 联调（可选）：设置 OSS_IT_ENABLED=true 与 OSS_ENDPOINT、OSS_ACCESS_KEY_ID、OSS_ACCESS_KEY_SECRET、
 * OSS_PRIVATE_BUCKET、OSS_PUBLIC_BUCKET（可选 OSS_REGION）后运行：
 * {@code mvn -o test -Dtest=OssLiveIT}。用测试存储桶，测试结束会删除上传的对象。
 */
@EnabledIfEnvironmentVariable(named = "OSS_IT_ENABLED", matches = "true")
class OssLiveIT {

    @TempDir
    Path tmp;

    @Test
    void putSignedUrlSizeDelete_againstRealBucket() throws Exception {
        StorageProperties.Oss cfg = new StorageProperties.Oss();
        cfg.setEndpoint(System.getenv("OSS_ENDPOINT"));
        cfg.setRegion(System.getenv("OSS_REGION"));
        cfg.setAccessKeyId(System.getenv("OSS_ACCESS_KEY_ID"));
        cfg.setAccessKeySecret(System.getenv("OSS_ACCESS_KEY_SECRET"));
        cfg.setPrivateBucket(System.getenv("OSS_PRIVATE_BUCKET"));
        cfg.setPublicBucket(System.getenv("OSS_PUBLIC_BUCKET"));
        cfg.setKeyPrefix("it");
        OssFileStorage s = new OssFileStorage(cfg);
        try {
            String key = StorageKeys.newKey(StorageArea.FINANCE, 0, "pdf");
            Path src = Files.writeString(tmp.resolve("a.pdf"), "%PDF-1.7 live");
            s.put(false, key, src, "application/pdf");
            assertThat(s.size(false, key)).isEqualTo(13);
            try (InputStream in = s.open(false, key)) {
                assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("%PDF-1.7 live");
            }
            String url = s.signedUrl(key, "水单.pdf", Instant.now().plusSeconds(60), true);
            HttpResponse<String> res = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(url)).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(res.statusCode()).isEqualTo(200);
            assertThat(res.headers().firstValue("Content-Disposition").orElse("")).contains("attachment");
            s.delete(false, key);
            assertThat(s.size(false, key)).isEqualTo(-1);
        } finally {
            s.destroy();
        }
    }
}
