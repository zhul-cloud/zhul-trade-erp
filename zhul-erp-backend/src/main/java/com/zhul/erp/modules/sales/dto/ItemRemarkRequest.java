package com.zhul.erp.modules.sales.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** 写型号备注（openspec item-remark）；空串表示清空 */
@Data
public class ItemRemarkRequest {
    @Size(max = 200, message = "型号备注不能超过 200 个字")
    private String remark;
}
