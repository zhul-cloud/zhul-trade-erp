package com.zhul.erp.modules.purchase.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 批量指派采购员；purchaserId 为空表示改为未指定 */
@Data
public class AssignRequirementsRequest {
    @NotEmpty(message = "请选择需求")
    @Size(max = 200, message = "一次最多 200 条")
    private List<Long> ids;
    private Long purchaserId;
}
