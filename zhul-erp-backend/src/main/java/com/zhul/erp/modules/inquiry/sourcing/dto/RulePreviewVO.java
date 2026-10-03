package com.zhul.erp.modules.inquiry.sourcing.dto;

import lombok.Data;

/** 按规则分配的预览 */
@Data
public class RulePreviewVO {
    private Long taskId;
    private String taskCode;
    private String brand;
    private String category;
    private Long assigneeId;
    private String assigneeName;
    /** 如「规则 1」「没有命中规则，按推荐」 */
    private String basis;
}
