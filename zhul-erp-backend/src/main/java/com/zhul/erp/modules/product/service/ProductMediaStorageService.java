package com.zhul.erp.modules.product.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 商品图片、视频文件的存储（商品模块自己的存储服务，不复用询盘附件的）。
 * 当前实现落本地磁盘（{@code zhul.upload.dir} 下的 {@code product} 子目录），后续迁移到 OSS 时只替换实现类。
 */
public interface ProductMediaStorageService {

    int MEDIA_IMAGE = 1;
    int MEDIA_VIDEO = 2;

    /**
     * 校验并保存文件。图片仅 jpg/jpeg/png/webp 且 ≤5MB，视频仅 mp4/webm 且 ≤100MB；
     * 同时校验扩展名和文件头，SVG 一律拒绝；存盘用随机文件名，不使用客户端文件名。
     */
    StoredMedia store(MultipartFile file, int mediaType);

    /** 尽力删除已保存的文件（数据库写入失败时清理），失败只记日志 */
    void deleteQuietly(String url);

    /** @param url 可访问的站内路径，如 /uploads/product/202609/xxxx.png */
    record StoredMedia(String url, long size) {
    }
}
