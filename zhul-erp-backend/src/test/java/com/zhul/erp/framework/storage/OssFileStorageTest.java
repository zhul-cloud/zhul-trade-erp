package com.zhul.erp.framework.storage;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.ObjectMetadata;
import com.zhul.erp.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** OSS 存储：用模拟客户端核对存储桶、键前缀、签名参数与公开地址 */
class OssFileStorageTest {

    private final OSS client = mock(OSS.class);

    private static StorageProperties.Oss cfg(String prefix, String domain) {
        StorageProperties.Oss c = new StorageProperties.Oss();
        c.setEndpoint("https://oss-cn-hangzhou.aliyuncs.com");
        c.setPrivateBucket("zhul-private");
        c.setPublicBucket("zhul-public");
        c.setKeyPrefix(prefix);
        c.setPublicDomain(domain);
        return c;
    }

    @Test
    void put_choosesBucketByVisibility_andAddsPrefix() {
        OssFileStorage s = new OssFileStorage(client, cfg("/prod/", null));
        s.put(false, "tenant/1/finance/202610/a.png", Path.of("/tmp/a.png"), "image/png");
        ArgumentCaptor<ObjectMetadata> meta = ArgumentCaptor.forClass(ObjectMetadata.class);
        verify(client).putObject(eq("zhul-private"), eq("prod/tenant/1/finance/202610/a.png"), any(File.class), meta.capture());
        assertThat(meta.getValue().getContentType()).isEqualTo("image/png");
        s.put(true, "shared/product/x/y/b.jpg", Path.of("/tmp/b.jpg"), "image/jpeg");
        verify(client).putObject(eq("zhul-public"), eq("prod/shared/product/x/y/b.jpg"), any(File.class), any(ObjectMetadata.class));
    }

    @Test
    void publicUrl_defaultBucketDomain_orCustomDomain() {
        assertThat(new OssFileStorage(client, cfg(null, null)).publicUrl("shared/product/x/y/b.jpg"))
                .isEqualTo("https://zhul-public.oss-cn-hangzhou.aliyuncs.com/shared/product/x/y/b.jpg");
        assertThat(new OssFileStorage(client, cfg("prod", "https://static.example.com/")).publicUrl("shared/a.jpg"))
                .isEqualTo("https://static.example.com/prod/shared/a.jpg");
    }

    @Test
    void signedUrl_privateBucket_expiry_andFileNameDisposition() throws Exception {
        when(client.generatePresignedUrl(any(GeneratePresignedUrlRequest.class))).thenReturn(new URL("https://signed.example/x"));
        OssFileStorage s = new OssFileStorage(client, cfg(null, null));
        Instant exp = Instant.now().plusSeconds(600);
        assertThat(s.signedUrl("tenant/1/purchase/202610/a.pdf", "合同 ABC.pdf", exp, true)).isEqualTo("https://signed.example/x");
        ArgumentCaptor<GeneratePresignedUrlRequest> req = ArgumentCaptor.forClass(GeneratePresignedUrlRequest.class);
        verify(client).generatePresignedUrl(req.capture());
        assertThat(req.getValue().getBucketName()).isEqualTo("zhul-private");
        assertThat(req.getValue().getKey()).isEqualTo("tenant/1/purchase/202610/a.pdf");
        assertThat(req.getValue().getExpiration().toInstant().getEpochSecond()).isEqualTo(exp.getEpochSecond());
        assertThat(req.getValue().getResponseHeaders().getContentDisposition())
                .isEqualTo("attachment; filename*=UTF-8''%E5%90%88%E5%90%8C%20ABC.pdf");
    }

    @Test
    void size_missingIsMinusOne() {
        when(client.doesObjectExist("zhul-private", "k.pdf")).thenReturn(false);
        assertThat(new OssFileStorage(client, cfg(null, null)).size(false, "k.pdf")).isEqualTo(-1);
    }

    @Test
    void open_noSuchKey_becomesBizMessage() {
        when(client.getObject("zhul-private", "k.pdf"))
                .thenThrow(new OSSException("not found", "NoSuchKey", "req", "host", null, null, null));
        BizException e = assertThrows(BizException.class, () -> new OssFileStorage(client, cfg(null, null)).open(false, "k.pdf"));
        assertThat(e.getMessage()).contains("附件不存在");
    }
}
