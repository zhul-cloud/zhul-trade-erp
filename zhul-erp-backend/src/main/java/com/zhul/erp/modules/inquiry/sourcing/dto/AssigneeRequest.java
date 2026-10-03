package com.zhul.erp.modules.inquiry.sourcing.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 改派或追加比价 */
@Data
public class AssigneeRequest {
    @NotNull(message = "请选择采购人员")
    private Long assigneeId;
}
