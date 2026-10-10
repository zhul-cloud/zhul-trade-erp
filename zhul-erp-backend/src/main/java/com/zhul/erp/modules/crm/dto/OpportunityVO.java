package com.zhul.erp.modules.crm.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 商机列表行 */
@Data
public class OpportunityVO {

    private Long id;
    private String opportunityCode;
    private Long customerId;
    /** 客户展示名：有客户名称用名称，否则用联系人名称 */
    private String customerName;
    private boolean customerNameMissing;
    private String country;
    private Integer sourceChannel;
    private LocalDate firstContactDate;
    private Long ownerId;
    private String ownerName;
    private String stageCode;
    private String stageName;
    /** 阶段类别：1-进行中、2-赢单、3-输单、4-无效 */
    private Integer stageCategory;
    private String demandSummary;
    private LocalDateTime updateTime;
    private String updateBy;
    private LocalDateTime createTime;
    private String createBy;
}
