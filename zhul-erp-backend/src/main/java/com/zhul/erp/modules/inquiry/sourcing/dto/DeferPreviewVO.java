package com.zhul.erp.modules.inquiry.sourcing.dto;

import lombok.Data;

/** 延后整张询盘前的确认信息 */
@Data
public class DeferPreviewVO {
    private Long customerInquiryId;
    private String inquiryCode;
    private Integer level;
    private String customerName;
    private String salesName;
    /** 将延后的待分配任务数与型号数 */
    private Integer taskCount;
    private Integer itemCount;
    /** 这张询盘下不在待分配的任务数（不受影响） */
    private Integer otherCount;
}
