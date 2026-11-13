package com.zhul.erp.modules.inquiry.sourcing.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 「已延后」列表的一行：同一张询盘、延后到同一天的任务合成一行 */
@Data
public class DeferredGroupVO {
    private LocalDate deferUntil;
    private Long customerInquiryId;
    private String inquiryCode;
    private Integer level;
    private String customerName;
    private String salesName;
    private List<Long> taskIds;
    /** 只有一个任务时的任务编号、品牌与品类 */
    private String taskCode;
    private String brand;
    private String category;
    private Integer taskCount;
    private Integer itemCount;
    /** 这张询盘待分配的任务都延后了 */
    private Boolean wholeInquiry;
    private String reason;
    private String deferByName;
    private LocalDateTime deferTime;
}
