package com.zhul.erp.modules.crm.followup.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/** 保存战略客户的当年年度计划（第三期 design D7）：目标金额为本位币 CNY，保留 2 位小数（HALF_UP） */
@Data
public class AnnualPlanRequest {
    @NotNull(message = "请填写年度目标")
    @DecimalMin(value = "0.00", message = "年度目标不能为负数")
    @DecimalMax(value = "9999999999999999.99", message = "年度目标金额过大")
    @Digits(integer = 16, fraction = 2, message = "年度目标最多保留 2 位小数")
    private BigDecimal targetAmount;
    @Size(max = 200, message = "重点品类或品牌不能超过200个字")
    private String focus;
    @Size(max = 500, message = "备注不能超过500个字")
    private String note;
}
