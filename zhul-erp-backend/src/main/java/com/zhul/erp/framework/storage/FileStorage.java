package com.zhul.erp.framework.storage;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;

/**
 * 一种存储方式（本地目录或阿里云 OSS）。业务代码通过 {@link FileStorageService} 使用，只面对「存储方式 + 文件键」。
 * publicRead 为 true 的文件放公开存储，以固定地址访问；其余放私有存储，只能用签名链接访问。
 */
public interface FileStorage {

    String LOCAL = "LOCAL";
    String OSS = "OSS";

    /** 存储方式代码，写在各附件记录的 storage 字段 */
    String code();

    /** 把本地文件上传为 key */
    void put(boolean publicRead, String key, Path source, String contentType);

    InputStream open(boolean publicRead, String key);

    /** 文件大小；不存在时返回 -1 */
    long size(boolean publicRead, String key);

    void delete(boolean publicRead, String key);

    /** 在同一存储内复制文件（如实拍素材挂到任务后移到「品牌 / 型号」目录） */
    void copy(boolean publicRead, String fromKey, String toKey);

    /**
     * 私有文件的临时签名链接。
     *
     * @param fileName 下载时使用的文件名
     * @param download true 时强制下载（另存为），false 时浏览器内预览、播放
     */
    String signedUrl(String key, String fileName, Instant expiresAt, boolean download);

    /** 公开文件的固定地址 */
    String publicUrl(String key);
}
