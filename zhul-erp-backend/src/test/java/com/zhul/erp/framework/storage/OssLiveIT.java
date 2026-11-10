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

    /** 分片直传：签名地址真实 PUT 两片（16MB + 4MB），合并、读文件头、复制到正式键后删除（spec system/file-attachment「大文件分片直传」） */
    @Test
    void multipartPresignedUpload_againstRealBucket() throws Exception {
        StorageProperties.Oss cfg = new StorageProperties.Oss();
        cfg.setEndpoint(System.getenv("OSS_ENDPOINT"));
        cfg.setRegion(System.getenv("OSS_REGION"));
        cfg.setAccessKeyId(System.getenv("OSS_ACCESS_KEY_ID"));
        cfg.setAccessKeySecret(System.getenv("OSS_ACCESS_KEY_SECRET"));
        cfg.setPrivateBucket(System.getenv("OSS_PRIVATE_BUCKET"));
        cfg.setPublicBucket(System.getenv("OSS_PUBLIC_BUCKET"));
        cfg.setKeyPrefix("it");
        OssFileStorage s = new OssFileStorage(cfg);
        String tmpKey = "tmp/0/" + java.util.UUID.randomUUID().toString().replace("-", "") + ".pdf";
        String finalKey = StorageKeys.newKey(StorageArea.INQUIRY, 0, "pdf");
        try {
            byte[] data = new byte[20 * 1024 * 1024];
            java.util.Arrays.fill(data, (byte) 'x');
            System.arraycopy("%PDF-1.7".getBytes(StandardCharsets.US_ASCII), 0, data, 0, 8);
            int part = 16 * 1024 * 1024;
            String uploadId = s.initiateMultipart(tmpKey, "application/pdf");
            java.util.List<com.aliyun.oss.model.PartETag> etags = new java.util.ArrayList<>();
            HttpClient http = HttpClient.newHttpClient();
            for (int n = 1; n <= 2; n++) {
                byte[] chunk = java.util.Arrays.copyOfRange(data, (n - 1) * part, Math.min(data.length, n * part));
                String url = s.presignPart(tmpKey, uploadId, n, Instant.now().plusSeconds(600));
                HttpResponse<String> res = http.send(HttpRequest.newBuilder(URI.create(url))
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(chunk)).build(), HttpResponse.BodyHandlers.ofString());
                assertThat(res.statusCode()).as(res.body()).isEqualTo(200);
                etags.add(new com.aliyun.oss.model.PartETag(n, res.headers().firstValue("ETag").orElseThrow()));
            }
            s.completeMultipart(tmpKey, uploadId, etags);
            assertThat(s.size(false, tmpKey)).isEqualTo(data.length);
            assertThat(new String(s.readHead(tmpKey, 8), StandardCharsets.US_ASCII)).isEqualTo("%PDF-1.7");
            s.copy(false, tmpKey, finalKey);
            assertThat(s.size(false, finalKey)).isEqualTo(data.length);
        } finally {
            s.delete(false, tmpKey);
            s.delete(false, finalKey);
            s.destroy();
        }
    }
}
