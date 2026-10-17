package com.zhul.erp.modules.product.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 商品图片、视频文件的存储：共享商品库的公开存储，按「品牌 / 型号」归档（shared/product/{品牌}/{型号}/）。
 * 校验规则是商品模块自己的；存储走统一的文件存储（本地或 OSS），图片保存前压缩。
 */
public interface ProductMediaStorageService {

    int MEDIA_IMAGE = 1;
    int MEDIA_VIDEO = 2;

    /**
     * 校验并保存文件。图片仅 jpg/jpeg/png/webp 且 ≤5MB，视频仅 mp4/webm 且 ≤100MB；
     * 同时校验扩展名和文件头，SVG 一律拒绝；存储用随机文件名，不使用客户端文件名。
     */
    StoredMedia store(MultipartFile file, int mediaType, String brand, String model);

    /** 尽力删除已保存的文件（数据库写入失败、删除媒体时清理），失败只记日志 */
    void deleteQuietly(String storage, String fileKey);

    /** 公开地址的当前写法（本地为 /uploads/…，OSS 为存储桶或自定义域名） */
    String publicUrl(String storage, String fileKey);

    /**
     * @param url     公开访问地址
     * @param storage 存储方式（LOCAL、OSS）
     * @param fileKey 存储中的文件键
     */
    record StoredMedia(String url, long size, String storage, String fileKey) {
    }
}
