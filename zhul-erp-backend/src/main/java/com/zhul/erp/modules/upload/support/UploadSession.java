package com.zhul.erp.modules.upload.support;

import lombok.Data;

/** 一次直传的会话，存在 Redis，1 天过期；只能由发起它的租户与用户使用 */
@Data
public class UploadSession {
    private String id;
    private Integer tenantId;
    private String username;
    private String scene;
    private String fileName;
    /** 按文件名判断的扩展名；完成时再按内容核对 */
    private String ext;
    private long size;
    private long partSize;
    private int partCount;
    /** OSS 或 LOCAL */
    private String storage;
    /** 临时文件键（OSS）或私有上传目录下的相对路径（本地） */
    private String tmpKey;
    /** OSS 分片上传的 uploadId */
    private String ossUploadId;
    /** 本地分片接口的令牌 */
    private String token;
}
