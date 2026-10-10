package com.zhul.erp.modules.sales.dto;

import lombok.Data;

import java.time.LocalDate;

/** PI 转成订单：销售日期不传时取最早的有效水单 / 到账日期，没有时取当天 */
@Data
public class ConvertOrderRequest {
    private LocalDate salesDate;
}
