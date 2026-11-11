package com.zhul.erp.modules.upload.support;

import com.zhul.erp.framework.storage.StorageArea;

import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * 上传场景：业务登记允许的类型、大小上限与上传权限（spec system/file-attachment「上传场景」）。
 *
 * @param key         场景标识，前端准备上传时传入
 * @param area        文件保存的业务环节（决定存储目录）
 * @param exts        允许的扩展名（按文件内容识别后的扩展名）
 * @param maxBytes    单个文件上限
 * @param totalBytes  单据附件合计上限，0 表示不限
 * @param typeMessage 类型不符时的提示
 * @param allowed     当前用户能否在该场景上传
 */
public record UploadScene(String key, StorageArea area, Set<String> exts, long maxBytes, long totalBytes,
                          String typeMessage, BooleanSupplier allowed) {

    /** 「500MB」「2GB」这样的大小文字 */
    public static String sizeText(long bytes) {
        long gb = 1024L * 1024 * 1024;
        return bytes % gb == 0 ? bytes / gb + "GB" : bytes / 1024 / 1024 + "MB";
    }
}
