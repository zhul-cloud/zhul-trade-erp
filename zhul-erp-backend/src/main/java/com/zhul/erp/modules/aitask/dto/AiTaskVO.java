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
}
