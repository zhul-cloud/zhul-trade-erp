package com.zhul.erp.modules.sales.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 修改手动订单的费用：整组替换，必须填原因；expectedTotal 为页面上看到的原合计，防止两人同时修改 */
@Data
public class UpdateOrderFeesRequest {
    @Valid
    @Size(max = 10, message = "费用最多 10 行")
    private List<OrderFeeLine> fees;
    @NotBlank(message = "请填写修改原因")
    @Size(max = 200, message = "修改原因不能超过 200 字")
    private String reason;
    @NotNull(message = "缺少原合计")
    private BigDecimal expectedTotal;
}
