package com.zhul.erp.modules.inquiry.sourcing.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/** 分配：批量分给同一人 */
@Data
public class AssignRequest {
    @NotEmpty(message = "请选择任务")
    private List<Long> taskIds;
    @NotNull(message = "请选择采购人员")
    private Long assigneeId;
}
