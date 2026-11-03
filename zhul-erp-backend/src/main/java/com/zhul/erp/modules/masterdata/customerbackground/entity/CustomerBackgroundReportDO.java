package com.zhul.erp.modules.masterdata.customerbackground.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 客户背调报告（spec crm/customer-background「背调报告」）：每次 AI 背调或上传一份，是当时的快照 */
@Data
@TableName("customer_background_report")
public class CustomerBackgroundReportDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    /** BG + yyyyMMdd + 3 位序号 */
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
    private String reportMarkdown;
    /** 字段建议及采纳情况（JSON 数组） */
    private String suggestions;
    private String uploadFileKey;
    private String uploadFileName;
    private Long aiTaskId;
    private String model;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer webSearches;
    private Integer durationSeconds;
    private Long requestedBy;
    private LocalDateTime completedAt;
    private Long deletedBy;
    private LocalDateTime deletedAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT)
    private String createBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
}
