package com.zhul.erp.modules.inquiry.sourcing.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/** 延后待分配的任务：给任务 ID，或给询盘 ID 延后这张询盘下全部待分配的任务（二选一） */
@Data
public class DeferRequest {
    @Size(max = 200, message = "一次最多 200 个任务")
    private List<Long> taskIds;
    private Long customerInquiryId;
    @NotNull(message = "请选择延后到哪天")
    private LocalDate deferUntil;
    @Size(max = 100, message = "原因最多 100 字")
    private String reason;
}
