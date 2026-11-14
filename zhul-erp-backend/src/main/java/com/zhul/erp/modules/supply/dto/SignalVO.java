package com.zhul.erp.modules.supply.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 需求信号（待归类列表、需求对象时间线） */
@Data
public class SignalVO {
    private Long id;
    private Integer sourceType;
    private Long sourceDocId;
    private String sourceDocNo;
    private Long demandId;
    private Long brandId;
    private String brandName;
    private String brandRaw;
    private Long categoryId;
    private String modelRaw;
    private Integer quantity;
    private String reasonText;
    private String content;
    private String platform;
    private Integer occurCount;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private String linkUrl;
    private LocalDateTime occurredAt;
    private Boolean cancelled;
    private String operatorName;
}
