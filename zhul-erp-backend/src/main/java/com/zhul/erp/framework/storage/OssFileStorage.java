package com.zhul.erp.framework.storage;

import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.common.auth.DefaultCredentialProvider;
import com.aliyun.oss.common.comm.SignVersion;
import com.aliyun.oss.model.AbortMultipartUploadRequest;
import com.aliyun.oss.model.CompleteMultipartUploadRequest;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.GetObjectRequest;
import com.aliyun.oss.model.InitiateMultipartUploadRequest;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PartETag;
import com.aliyun.oss.model.SetBucketCORSRequest;
import com.aliyun.oss.model.ResponseHeaderOverrides;
import com.zhul.erp.common.exception.BizException;
import org.springframework.beans.factory.DisposableBean;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;
import java.util.List;

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

    // ---------- 分片直传（只用私有存储桶） ----------

    /** 发起分片上传，返回 uploadId */
    public String initiateMultipart(String key, String contentType) {
        ObjectMetadata meta = new ObjectMetadata();
        meta.setContentType(contentType);
        return client.initiateMultipartUpload(new InitiateMultipartUploadRequest(cfg.getPrivateBucket(), prefix + key, meta))
                .getUploadId();
    }

    /**
     * 某个分片的签名上传地址（PUT），浏览器直接上传；只对这个文件、这次上传、这一片有效。
     * Content-Length 一并签名：上传的字节数与 length 不同时 OSS 直接拒绝，不能借一个分片地址传超大内容。
     */
    public String presignPart(String key, String uploadId, int partNumber, long length, Instant expiresAt) {
        GeneratePresignedUrlRequest req = new GeneratePresignedUrlRequest(cfg.getPrivateBucket(), prefix + key, HttpMethod.PUT);
        req.setExpiration(Date.from(expiresAt));
        req.addHeader("Content-Length", String.valueOf(length));
        req.addAdditionalHeaderName("content-length");
        req.addQueryParameter("partNumber", String.valueOf(partNumber));
        req.addQueryParameter("uploadId", uploadId);
        return client.generatePresignedUrl(req).toString();
    }

    public void completeMultipart(String key, String uploadId, List<PartETag> parts) {
        client.completeMultipartUpload(new CompleteMultipartUploadRequest(cfg.getPrivateBucket(), prefix + key, uploadId, parts));
    }

    /** 中止分片上传，已传的分片随之删除；上传已结束或不存在时忽略 */
    public void abortMultipart(String key, String uploadId) {
        try {
            client.abortMultipartUpload(new AbortMultipartUploadRequest(cfg.getPrivateBucket(), prefix + key, uploadId));
        } catch (OSSException e) {
            if (!"NoSuchUpload".equals(e.getErrorCode())) {
                throw e;
            }
        }
    }

    /** 读取私有文件开头 n 个字节（按范围读取，不下载整个文件），用于按内容识别类型 */
    public byte[] readHead(String key, int n) {
        GetObjectRequest req = new GetObjectRequest(cfg.getPrivateBucket(), prefix + key);
        req.setRange(0, n - 1L);
        try (InputStream in = client.getObject(req).getObjectContent()) {
            return in.readNBytes(n);
        } catch (IOException e) {
            throw new UncheckedIOException("读取文件失败", e);
        }
    }

    /** 私有存储桶的跨域规则；读取失败（如没有权限）时抛出原异常，由调用方决定如何提示 */
    public List<SetBucketCORSRequest.CORSRule> privateCorsRules() {
        return client.getBucketCORSRules(cfg.getPrivateBucket());
    }

    private String bucket(boolean publicRead) {
        return publicRead ? cfg.getPublicBucket() : cfg.getPrivateBucket();
    }

    @Override
    public void destroy() {
        client.shutdown();
    }
}
