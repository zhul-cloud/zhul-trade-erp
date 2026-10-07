package com.zhul.erp.modules.sales.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 开 PI / 追加型号时选中的报价行 */
@Data
public class PiLineRequest {
    @NotNull(message = "缺少报价行")
    private Long quotationItemId;
    @Min(value = 1, message = "数量需要是正整数")
    /** 数量，不填时取报价单数量 */
    private Integer quantity;
}
