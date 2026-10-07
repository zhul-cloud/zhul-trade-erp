package com.zhul.erp.modules.quotation.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 报价单详情 */
@Data
public class QuotationVO {
    private Long id;
    private String quotationNo;
    private Long customerId;
    private String customerName;
    private Integer customerType;
    private Long ownerId;
    private String ownerName;
    private String currencyCode;
    private BigDecimal exchangeRate;
    private LocalDateTime rateTime;
    private String incoterm;
    private String incotermPlace;
    private LocalDate validUntil;
    private String remark;
    private Integer status;
    private String statusName;
    private String lostReason;
    private String lostReasonName;
    private String lostNote;
    private Long copiedFromId;
    private String copiedFromNo;
    private BigDecimal itemAmount;
    private BigDecimal feeAmount;
    private BigDecimal totalAmount;
    private BigDecimal totalAmountCny;
    private BigDecimal netProfit;
    private BigDecimal netProfitCny;
    private BigDecimal marginRate;
    private LocalDateTime sentAt;
    private LocalDateTime closedAt;
    private LocalDateTime createTime;
    private Boolean editable;
    private List<QuotationItemVO> items;
    private List<QuotationFeeVO> fees;
    private List<SendLog> sendLogs;
    /** 来源询盘编号 */
    private List<String> inquiryCodes;
    /** 老客户提示：近 3 张已成交报价单的合计毛利率；不是老客户或提示已停用时为空 */
    private List<BigDecimal> returningCustomerMargins;
    private Boolean returningCustomer;
    /** 草稿的汇率快照与当前系统汇率不同时有值 */
    private BigDecimal systemRate;
    /** 新建时：所选询盘中还在询价、没有带入的型号数 */
    private Integer pendingItemCount;

    @Data
    public static class SendLog {
        private Integer channel;
        private String channelName;
        private String sentByName;
        private LocalDateTime sentAt;
    }
}
