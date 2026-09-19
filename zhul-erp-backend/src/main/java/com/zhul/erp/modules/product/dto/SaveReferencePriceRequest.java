package com.zhul.erp.modules.product.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 平台参考价：平台层面的参考信息，不是任何租户的报价或售价。 */
@Data
public class SaveReferencePriceRequest {
    /** 原币金额，大于 0，按 HALF_UP 保留 2 位 */
    private BigDecimal priceOriginal;
    /** 币种，ISO 4217 三位大写代码，必填 */
    private String currencyCode;
    /** 原币 → 人民币的汇率（保留 6 位）。币种为 CNY 时忽略并按 1 处理；非 CNY 未提供时本位币金额留空 */
    private BigDecimal exchangeRate;
    private String priceSource;
    private LocalDate priceDate;
}
