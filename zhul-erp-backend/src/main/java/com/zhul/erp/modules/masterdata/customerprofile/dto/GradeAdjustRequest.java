package com.zhul.erp.modules.masterdata.customerprofile.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 调整经营等级：1 上调一级、-1 下调一级、0 取消调整 */
@Data
public class GradeAdjustRequest {
    @NotNull(message = "请选择调整方向")
    @Min(value = -1, message = "只能上调或下调一级")
    @Max(value = 1, message = "只能上调或下调一级")
    private Integer adjust;
    @Size(max = 200, message = "调整原因不能超过 200 字")
    private String reason;
}
