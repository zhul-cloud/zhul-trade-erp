package com.zhul.erp.framework.storage;

import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.common.auth.DefaultCredentialProvider;
import com.aliyun.oss.common.comm.SignVersion;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.ResponseHeaderOverrides;
import com.zhul.erp.common.exception.BizException;
import org.springframework.beans.factory.DisposableBean;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;

/**
 * 阿里云 OSS 存储：私有文件放私有存储桶，只能用签名链接访问；公开文件放公开存储桶，以固定地址访问（可配置自定义域名）。
 * 配置的键前缀（多个环境共用存储桶时区分用）只在这里拼接，数据库里的文件键不带前缀。
 */
public class OssFileStorage implements FileStorage, DisposableBean {

    private static final String NO_SUCH_KEY = "NoSuchKey";

    private final OSS client;
    private final StorageProperties.Oss cfg;
    private final String prefix;

    public OssFileStorage(StorageProperties.Oss cfg) {
        this(buildClient(cfg), cfg);
    }

    /** 测试用：注入模拟的 OSS 客户端 */
    OssFileStorage(OSS client, StorageProperties.Oss cfg) {
        this.client = client;
        this.cfg = cfg;
        String p = cfg.getKeyPrefix() == null ? "" : cfg.getKeyPrefix().trim().replaceAll("^/+|/+$", "");
        this.prefix = p.isEmpty() ? "" : p + "/";
    }

    private static OSS buildClient(StorageProperties.Oss cfg) {
        ClientBuilderConfiguration conf = new ClientBuilderConfiguration();
        OSSClientBuilder.OSSClientBuilderImpl builder = OSSClientBuilder.create()
                .endpoint(cfg.getEndpoint())
                .credentialsProvider(new DefaultCredentialProvider(cfg.getAccessKeyId(), cfg.getAccessKeySecret()))
                .clientConfiguration(conf);
        if (cfg.getRegion() != null && !cfg.getRegion().isBlank()) {
            conf.setSignatureVersion(SignVersion.V4);
            builder.region(cfg.getRegion());
        }
        return builder.build();
    }

    @Override
    public String code() {
        return OSS;
    }

    @Override
    public void put(boolean publicRead, String key, Path source, String contentType) {
        ObjectMetadata meta = new ObjectMetadata();
        meta.setContentType(contentType);
        client.putObject(bucket(publicRead), prefix + key, source.toFile(), meta);
    }

    @Override
    public InputStream open(boolean publicRead, String key) {
        try {
            return client.getObject(bucket(publicRead), prefix + key).getObjectContent();
        } catch (OSSException e) {
            if (NO_SUCH_KEY.equals(e.getErrorCode())) {
                throw new BizException("附件不存在，请重新上传");
            }
            throw e;
        }
    }

    @Override
    public long size(boolean publicRead, String key) {
        String bucket = bucket(publicRead);
        if (!client.doesObjectExist(bucket, prefix + key)) {
            return -1;
        }
        return client.getObjectMetadata(bucket, prefix + key).getContentLength();
    }

    @Override
    public void delete(boolean publicRead, String key) {
        client.deleteObject(bucket(publicRead), prefix + key);
    }

    @Override
    public void copy(boolean publicRead, String fromKey, String toKey) {
        String bucket = bucket(publicRead);
        client.copyObject(bucket, prefix + fromKey, bucket, prefix + toKey);
    }

    @Override
    public String signedUrl(String key, String fileName, Instant expiresAt, boolean download) {
        GeneratePresignedUrlRequest req = new GeneratePresignedUrlRequest(cfg.getPrivateBucket(), prefix + key, HttpMethod.GET);
        req.setExpiration(Date.from(expiresAt));
        ResponseHeaderOverrides headers = new ResponseHeaderOverrides();
        headers.setContentDisposition((download ? "attachment" : "inline") + "; filename*=UTF-8''"
                + URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20"));
        req.setResponseHeaders(headers);
        return client.generatePresignedUrl(req).toString();
    }

    @Override
    public String publicUrl(String key) {
        String domain = cfg.getPublicDomain();
        if (domain == null || domain.isBlank()) {
            String host = cfg.getEndpoint().replaceFirst("^https?://", "");
            domain = "https://" + cfg.getPublicBucket() + "." + host;
        }
        return domain.replaceAll("/+$", "") + "/" + prefix + key;
    }

    private String bucket(boolean publicRead) {
        return publicRead ? cfg.getPublicBucket() : cfg.getPrivateBucket();
    }

    @Override
    public void destroy() {
        client.shutdown();
    }
}
