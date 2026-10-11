package com.zhul.erp.modules.finance.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/** 财务登记报销已付 */
@Data
public class PayReimbursementRequest {
    @NotNull(message = "请选择付款账户")
    private Long bankAccountId;
    @NotNull(message = "请填写付款日期")
    private LocalDate payDate;
    @NotEmpty(message = "请上传转账截图")
    private List<Long> attachmentIds;
    @Size(max = 500)
    private String remark;
}
