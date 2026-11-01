package com.zhul.erp.modules.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 余额流水 */
@Data
public class CreditEntryVO {
    private Long id;
    private Integer entryType;
    private String typeName;
    private BigDecimal amount;
    private BigDecimal amountCny;
    private String sourceType;
    private Long sourceId;
    private String sourceNo;
    private String remark;
    private LocalDateTime createTime;
    private String createBy;
}
