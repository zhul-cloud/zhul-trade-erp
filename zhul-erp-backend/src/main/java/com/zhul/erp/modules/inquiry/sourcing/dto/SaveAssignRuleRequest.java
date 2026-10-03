package com.zhul.erp.modules.inquiry.sourcing.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 新增或编辑分配规则 */
@Data
public class SaveAssignRuleRequest {
    @NotNull(message = "请选择匹配方式")
    @Min(value = 1, message = "匹配方式不正确")
    @Max(value = 2, message = "匹配方式不正确")
    private Integer matchType;
    @NotEmpty(message = "请填写匹配值")
    @Size(max = 20, message = "匹配值最多 20 个")
    private List<String> matchValues;
    @NotNull(message = "请选择采购人员")
    private Long assigneeId;
    private Integer status;
}
