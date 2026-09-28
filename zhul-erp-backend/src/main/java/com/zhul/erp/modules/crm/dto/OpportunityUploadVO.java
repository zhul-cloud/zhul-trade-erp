package com.zhul.erp.modules.crm.dto;

import lombok.Data;

/** 上传结果：登记或编辑商机时带上 fileKey 才生效 */
@Data
public class OpportunityUploadVO {

    private String fileKey;
    private String fileName;
    private Long fileSize;
    private String contentType;
}
