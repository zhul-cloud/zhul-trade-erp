package com.zhul.erp.modules.masterdata.customerbackground.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 背调报告（列表、发起与上传的结果）：编号、来源、状态与核心结论 */
@Data
public class BackgroundReportVO {
    private Long id;
    private String reportNo;
    private Long customerId;
    /** 来源（1-AI背调、2-上传） */
    private Integer source;
    /** 状态（1-背调中、2-已完成、3-失败） */
    private Integer status;
    private String failReason;
    /** 跟进优先级（0-未出结论、1-立即重点跟进、2-正常跟进、3-低优先级、4-不建议跟进） */
    private Integer priority;
    /** 综合评分 1–5，0 为未出结论 */
    private Integer score;
    private String mainConflict;
    private String firstAction;
    private String identityNote;
    private String supplyNote;
    private String keyFinding;
    private String obstacle;
    /** 字段建议条数 */
    private Integer suggestionCount;
    private Long requestedBy;
    private String requestedByName;
    private LocalDateTime createTime;
    private LocalDateTime completedAt;
    /** 给操作人的提示，如上传的文字过长已截断 */
    private String notice;
}
