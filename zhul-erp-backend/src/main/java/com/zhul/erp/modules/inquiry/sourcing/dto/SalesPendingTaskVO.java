package com.zhul.erp.modules.inquiry.sourcing.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 询盘负责人「待认领」的询价任务 */
@Data
public class SalesPendingTaskVO {
    private Long id;
    private String taskCode;
    private Long customerInquiryId;
    private String inquiryCode;
    private Integer level;
    private String customerName;
    private LocalDate quoteDeadline;
    private LocalDateTime confirmedAt;
    private Boolean urgent;
    private String brand;
    private String category;
    private Integer itemCount;
    private LocalDateTime salesDeadline;
    /** 距自动转给采购的分钟数，已到期为 0 */
    private Long remainingMinutes;
}
