package com.zhul.erp.modules.inquiry.customerinquiry.dto;

import lombok.Data;

/** 询盘单附件 */
@Data
public class AttachmentVO {
    private Long id;
    private String fileName;
    private String contentType;
    private Long fileSize;
    /** 类别（1-询盘附件、2-铭牌图片） */
    private Integer kind;
    /** 铭牌图片对应的型号 ID，0 为未对应 */
    private Long inquiryItemId;
}
