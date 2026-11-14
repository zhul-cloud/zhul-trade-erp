package com.zhul.erp.modules.supply.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 需求对象（列表行、型号清单、专项型号共用），码值见 SupplyConstants */
@Data
public class DemandVO {
    private Long id;
    private Long brandId;
    private String brandName;
    private Long categoryId;
    private String categoryName;
    private String model;
    private Integer status;
    private String pauseReason;
    private Integer demandLevel;
    private Integer priority;
    private Integer suggestedPriority;
    private String priorityRule;
    private Boolean priorityManual;
    private String priorityReason;
    private Integer cntInquiry;
    private Integer cntOrder;
    private Integer cntStockout;
    private Integer cntLost;
    private Integer cntSearch;
    private Integer cntResearch;
    private LocalDateTime lastSignalAt;
    private Integer coverageStatus;
    private Integer suggestedCoverage;
    private Boolean coveragePending;
    private String coverageBasis;
    private String coverageNote;
    private LocalDate evidenceDate;
    private Boolean evidenceValid;
    private Long ownerId;
    private String ownerName;
    private String nextAction;
    private LocalDate nextActionDate;
    private Integer nextActionSource;
    private Integer reminderType;
    private Integer deferCount;
    /** 询价中的任务编号，没有为空 */
    private String sourcingTask;
    private Integer channelCount;
    private Boolean singleChannel;
    /** 有查看货源信息权限时才有 */
    private String primarySupplier;
    private Long activeProjectId;
    private LocalDateTime createTime;
    private String createBy;
    private LocalDateTime updateTime;
    private String updateBy;
}
