package com.zhul.erp.modules.sales.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 收款记录（水单 / 到账） */
@Data
public class ReceiptVO {
    private Long id;
    /** 1-水单、2-到账 */
    private Integer kind;
    private BigDecimal amount;
    private BigDecimal amountCny;
    private BigDecimal feeDiff;
    private LocalDate receiptDate;
    private Integer bankAccountId;
    /** 收款账户（脱敏） */
    private String bankAccountName;
    private Long slipId;
    private List<SlipFile> files;
    private String note;
    /** 1-有效、2-已作废 */
    private Integer status;
    private String voidReason;
    private String operatorName;
    /** 水单已有对应到账 */
    private Boolean matched;
    private LocalDateTime createTime;

    /** 水单附件 */
    @Data
    public static class SlipFile {
        private String fileKey;
        private String fileName;
    }
}
