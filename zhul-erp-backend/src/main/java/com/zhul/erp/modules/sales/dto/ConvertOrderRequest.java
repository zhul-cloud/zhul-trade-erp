package com.zhul.erp.modules.sales.dto;

import lombok.Data;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** PI 转成订单：销售日期不传时取最早的有效水单 / 到账日期，没有时取当天 */
@Data
public class ConvertOrderRequest {
    private LocalDate salesDate;
    /** 先采购后收款：PI 没有水单也没有到账时必须为 true，并填写原因；有水单或到账时忽略 */
    private Boolean payLater;
    @Size(max = 200, message = "先采购后收款的原因不能超过 200 个字")
    private String payLaterReason;
}
