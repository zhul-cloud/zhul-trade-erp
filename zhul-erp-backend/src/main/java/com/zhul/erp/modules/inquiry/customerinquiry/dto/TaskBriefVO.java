package com.zhul.erp.modules.inquiry.customerinquiry.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 客户询盘详情中的询价任务 */
@Data
public class TaskBriefVO {
    private Long id;
    private String taskCode;
    private String brand;
    private String category;
    private Integer itemCount;
    private Integer pricedCount;
    private Integer status;
    private Boolean timeout;
    private LocalDateTime firstAssignedAt;
    private List<String> assigneeNames;
    private Integer returnReason;
    private String returnNote;
}
