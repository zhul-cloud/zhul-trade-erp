package com.zhul.erp.modules.finance.dto;

import com.zhul.erp.modules.sales.dto.ReceiptRowVO;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 结汇单 */
@Data
public class FxSettlementVO {
    private Long id;
    private String settleNo;
    private String currencyCode;
    private LocalDate settleDate;
    private BigDecimal amount;
    private BigDecimal amountCny;
    private BigDecimal exchangeRate;
    private Integer bankAccountId;
    private String bankAccountName;
    private String remark;
    private Boolean migrated;
    /** 1-有效、2-已作废 */
    private Integer status;
    private String voidReason;
    private String voidedBy;
    private LocalDateTime voidedAt;
    private Integer allocationCount;
    /** 是否该币种最新的一张有效结汇单（只有它能作废） */
    private Boolean latest;
    private List<Allocation> allocations;
    private LocalDateTime createTime;
    private String createBy;
    private LocalDateTime updateTime;
    private String updateBy;

    @Data
    public static class Allocation {
        private BigDecimal amount;
        private BigDecimal amountCny;
        private ReceiptRowVO receipt;
    }
}
