package com.zhul.erp.modules.sales.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/** 新建费用订单：在原订单上单独向客户收取的费用（如客户改用我们的货代后的运费） */
@Data
public class CreateFeeOrderRequest {
    /** 不传时为当天，不能晚于当天 */
    private LocalDate salesDate;
    @Valid
    @Size(max = 10, message = "费用最多 10 行")
    private List<OrderFeeLine> fees;
    @Size(max = 500, message = "备注不能超过 500 字")
    private String remark;
    @Size(max = 10, message = "附件最多 10 个")
    private List<Long> attachmentIds;
}
