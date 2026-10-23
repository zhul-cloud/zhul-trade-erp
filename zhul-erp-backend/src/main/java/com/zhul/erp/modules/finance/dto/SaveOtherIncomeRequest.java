package com.zhul.erp.modules.finance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 登记 / 修改其他收入 */
@Data
public class SaveOtherIncomeRequest {
    @NotNull(message = "请填写日期")
    private LocalDate incomeDate;
    @NotBlank(message = "请选择类别")
    private String category;
    @NotNull(message = "请填写金额")
    @DecimalMin(value = "0.01", message = "金额须大于 0")
    private BigDecimal amount;
    @NotBlank(message = "请填写说明")
    @Size(max = 500, message = "说明不能超过 500 个字符")
    private String remark;
    /** 所属事业部（公司设置了事业部时有效）：为空表示「公共」，按销售额比例分摊 */
    private Integer businessUnitId;
    private List<Long> attachmentIds;
}
