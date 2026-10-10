package com.zhul.erp.modules.inquiry.sourcing.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 我的询价任务列表行（不含客户信息） */
@Data
public class MyTaskVO {
    private Long id;
    private String taskCode;
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
    /** 回价是否还能修改：业务员报价（或询盘取消）后为 false */
    private Boolean editable;
    private String brand;
    private String category;
    private Integer itemCount;
    /** 本人已提交或暂存了结果的型号数 */
    private Integer filledCount;
    private Integer status;
    private Boolean urgent;
    private Boolean timeout;
    /** 距超时还剩的分钟数，已超时为负 */
    private Long remainingMinutes;
    private LocalDateTime assignedAt;
    /** 任务更新时间与本人在该任务最近一次保存回价时间的较晚者 */
    private LocalDateTime updateTime;
    /** 是否多人比价 */
    private Boolean shared;
}
