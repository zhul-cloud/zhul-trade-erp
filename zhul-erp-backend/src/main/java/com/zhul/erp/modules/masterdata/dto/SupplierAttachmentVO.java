package com.zhul.erp.modules.masterdata.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 附件出参，不含存储路径；文件经下载接口获取 */
@Data
public class SupplierAttachmentVO {

    private Long id;
    /** 附件类型（1-营业执照、2-开户许可证、3-资质证书、4-合同、5-其他） */
    private Integer category;
    private String fileName;
    private Long fileSize;
    private String contentType;
    /** 上传人 */
    private String createBy;
    /** 上传时间 */
    private LocalDateTime createTime;
}
