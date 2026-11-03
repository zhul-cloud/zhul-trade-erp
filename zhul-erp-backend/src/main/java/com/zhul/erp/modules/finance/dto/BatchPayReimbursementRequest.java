package com.zhul.erp.modules.finance.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.List;

/** 财务批量登记报销已付：付款信息与单张登记相同，ids 为所选报销单 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class BatchPayReimbursementRequest extends PayReimbursementRequest {
    @NotEmpty(message = "请选择报销单")
    private List<Long> ids;
}
