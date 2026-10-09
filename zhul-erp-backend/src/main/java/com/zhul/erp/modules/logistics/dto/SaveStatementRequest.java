package com.zhul.erp.modules.logistics.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 新建对账单（货代 + 月份）或保存核对结果（逐票对账金额与说明） */
@Data
public class SaveStatementRequest {
    /** 新建时必填 */
    private Long forwarderId;
    /** 新建时必填，YYYY-MM */
    @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$", message = "对账月份格式为 2026-10")
    private String period;
    @Valid
    private List<Line> lines;

    @Data
    public static class Line {
        @NotNull
        private Long id;
        @NotNull(message = "请填写对账金额")
        @DecimalMin(value = "0", message = "对账金额不能为负数")
        @Digits(integer = 16, fraction = 2, message = "金额最多两位小数")
        private BigDecimal statementAmount;
        @Size(max = 200, message = "说明不能超过200个字")
        private String note;
    }
}
