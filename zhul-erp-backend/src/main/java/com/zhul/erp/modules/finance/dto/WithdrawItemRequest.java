package com.zhul.erp.modules.finance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 撤回待报销项（openspec po-cancel-refund）：撤回即作废该项 */
@Data
public class WithdrawItemRequest {
    /** 1-采购货款、4-费用单、5-收到退款；快递费、退货运费不能撤回 */
    @NotNull
    private Integer sourceType;
    @NotNull
    private Long sourceId;
    @NotBlank(message = "请填写撤回原因")
    @Size(max = 200, message = "撤回原因不能超过 200 个字符")
    private String reason;
}
