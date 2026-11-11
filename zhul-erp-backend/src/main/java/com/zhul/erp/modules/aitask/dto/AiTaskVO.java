package com.zhul.erp.modules.aitask.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiTaskVO {
    private Long id;
    private String skillId;
    private Integer status;
    private String output;
    private String errorMessage;
    private Long requestedBy;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createTime;
    /** 分批解析进度：已完成批数 / 总批数，总批数为 0 表示没有分批或还没开始报告 */
    private Integer progressDone;
    private Integer progressTotal;
    /** 分批解析当前阶段：read / extract / merge / verify，空为未报告 */
    private String progressStage;
}
