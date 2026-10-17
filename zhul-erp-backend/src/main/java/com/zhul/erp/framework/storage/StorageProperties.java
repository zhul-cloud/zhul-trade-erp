package com.zhul.erp.framework.storage;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 文件存储配置（zhul.storage.*）：存储方式、签名链接有效期、视频转码队列、阿里云 OSS。
 * OSS 访问密钥只从部署配置注入，toString 不输出。
 */
@Data
@ConfigurationProperties(prefix = "zhul.storage")
public class StorageProperties {

    /** 存储方式：local（服务器目录）或 oss（阿里云 OSS） */
    private String type = "local";

    /** 私有文件签名链接的有效期（秒） */
    private int signedUrlTtlSeconds = 600;

    /** 视频转码：同时转码的数量与排队上限 */
    private int transcodeThreads = 1;
    private int transcodeQueueCapacity = 100;
    /** ffmpeg 可执行文件，默认从 PATH 查找 */
    private String ffmpegPath = "ffmpeg";

    private Oss oss = new Oss();

    public boolean ossSelected() {
        return "oss".equalsIgnoreCase(type);
    }

    @Data
    public static class Oss {
        private String endpoint;
        private String region;
        @ToString.Exclude
        private String accessKeyId;
        @ToString.Exclude
        private String accessKeySecret;
        private String privateBucket;
        private String publicBucket;
        /** 公开存储的自定义域名（CDN），如 https://cdn.example.com；为空时用存储桶默认域名 */
        private String publicDomain;
        /** 多个环境共用存储桶时的键前缀，如 prod */
        private String keyPrefix;

        /** 缺少的必填项（配置项名），为空表示配置完整 */
        public List<String> missing() {
            List<String> m = new ArrayList<>(6);
            if (blank(endpoint)) {
                m.add("OSS_ENDPOINT");
            }
            if (blank(accessKeyId)) {
                m.add("OSS_ACCESS_KEY_ID");
            }
            if (blank(accessKeySecret)) {
                m.add("OSS_ACCESS_KEY_SECRET");
            }
            if (blank(privateBucket)) {
                m.add("OSS_PRIVATE_BUCKET");
            }
            if (blank(publicBucket)) {
                m.add("OSS_PUBLIC_BUCKET");
            }
            return m;
        }

        private static boolean blank(String s) {
            return s == null || s.isBlank();
        }
    }
}
