package com.zhul.erp.modules.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 业务凭证页的月份行（已结账月份） */
@Data
public class VoucherMonthVO {
    private String month;
    /** 是否已生成凭证；false 为上线前结账、还没有凭证的月份 */
    private Boolean generated;
    private Integer voucherCount;
    private BigDecimal debitTotal;
    private String currencyCode;
    /** 核对差额：期初 + 凭证里库存商品净额 + 不生成凭证的变动 − 期末；0 为相符 */
    private BigDecimal reconDiff;
    private Integer exportCount;
    private String exportedByName;
    private LocalDateTime exportedAt;
}
