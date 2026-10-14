package com.zhul.erp.modules.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 其他收入 */
@Data
public class OtherIncomeVO {
    private Long id;
    private String incomeNo;
    private LocalDate incomeDate;
    private String category;
    private String categoryName;
    private BigDecimal amount;
    private String remark;
    private Integer status;
    private String voidReason;
    private List<PayableVO.AttachmentRef> attachments;
    private LocalDateTime createTime;
    private String createBy;
    private LocalDateTime updateTime;
    private String updateBy;
}
