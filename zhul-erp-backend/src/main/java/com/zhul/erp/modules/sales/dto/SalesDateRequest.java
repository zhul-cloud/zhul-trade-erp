package com.zhul.erp.modules.sales.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/** 修改销售日期 */
@Data
public class SalesDateRequest {
    @NotNull(message = "请选择销售日期")
    private LocalDate salesDate;
}
