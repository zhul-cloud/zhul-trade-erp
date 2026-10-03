package com.zhul.erp.modules.inquiry.sourcing.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 分配工作台中的一个询价任务 */
@Data
public class BoardTaskVO {
    private Long id;
    private String taskCode;
    private Long customerInquiryId;
    /** 所属业务员（客户询盘负责人） */
    private Long salesId;
    private String salesName;
    /** 询盘等级（字典 inquiry_level 码值，越小越优先） */
    private Integer level;
    /** 新老客户（1-新客户、2-老客户） */
    private Integer customerType;
    /** 客户名称；兼职采购看不到，为空 */
    private String customerName;
    /** 客户询盘的报价截止日期 */
    private java.time.LocalDate quoteDeadline;
    private String brand;
    private String category;
    private Integer itemCount;
    private Integer pricedCount;
    private Boolean urgent;
    private Integer status;
    private Boolean timeout;
    /** 待分配：从生成或被退回起等待的分钟数 */
    private Long waitingMinutes;
    private LocalDateTime firstAssignedAt;
    private Integer returnReason;
    private String returnReasonLabel;
    private String returnNote;
    private String returnedByName;
    private Long recommendedId;
    private String recommendedName;
    /** 推荐理由，如「Mitsubishi 询过 23 次 · 进行中 8」 */
    private String recommendReason;
    private List<PurchaserVO> assignees;
}
