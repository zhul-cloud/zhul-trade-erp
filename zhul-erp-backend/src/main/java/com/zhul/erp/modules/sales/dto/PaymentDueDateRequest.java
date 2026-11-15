package com.zhul.erp.modules.sales.dto;

import lombok.Data;

import java.time.LocalDate;

/** 修改约定付款日；为空表示清空 */
@Data
public class PaymentDueDateRequest {
    private LocalDate paymentDueDate;
}
