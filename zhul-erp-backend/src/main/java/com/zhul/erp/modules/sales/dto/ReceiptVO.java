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
    private String paymentMethod;
    private String paymentMethodName;
    /** 1-线下、2-线上 */
    private Integer channel;
    private String platformOrderNo;
    private BigDecimal platformFee;
    /** 实收（原币）与实收人民币、汇率及来源（1-系统汇率、2-实际入账），仅到账 */
    private BigDecimal netAmount;
    private BigDecimal netAmountCny;
    private BigDecimal exchangeRate;
    private Integer rateSource;
    private String payer;
    /** 认领来的到账：认领人与时间 */
    private String claimedByName;
    private java.time.LocalDateTime claimedAt;
    /** 当前用户能否作废（有「登记到账」权限，或自己登记的平台收款） */
    private Boolean voidable;
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
