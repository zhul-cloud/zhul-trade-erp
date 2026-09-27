package com.zhul.erp.modules.masterdata.dto;

import lombok.Data;

/** 上传结果：保存供应商时把 fileKey 连同类型一起提交，附件才生效 */
@Data
public class SupplierAttachmentUploadVO {

    private String fileKey;
    private String fileName;
    private Long fileSize;
    private String contentType;
}
