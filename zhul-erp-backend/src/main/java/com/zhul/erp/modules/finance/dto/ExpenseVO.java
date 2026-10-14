package com.zhul.erp.modules.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 费用单 */
@Data
public class ExpenseVO {
    private Long id;
    private String expenseNo;
    private String name;
    private String category;
    private String categoryName;
    private Integer amortizeMonths;
    /** 是否关联了订单型号（不关联的为运营成本） */
    private Boolean linked;
    private LocalDate occurDate;
    private Integer payeeType;
    private Long supplierId;
    private String shopPlatform;
    private String shopName;
    private String payeeName;
    private String currencyCode;
    private BigDecimal amount;
    private BigDecimal exchangeRate;
    private BigDecimal amountCny;
    private Integer method;
    private Long advanceUserId;
    private String advanceUserName;
    private String remark;
    /** 1-有效、2-已作废 */
    private Integer status;
    private String voidReason;
    /** 公司对公：应付单 ID 与状态、已付 */
    private Long payableId;
    private Integer payStatus;
    private BigDecimal paidAmount;
    /** 员工垫付：所在报销单编号与状态（1-待付款、2-已报销），没进报销单为空 */
    private String reimburseNo;
    private Integer reimburseStatus;
    /** 已付款或已进报销单：只能改名称、说明与分摊 */
    private boolean locked;
    private List<Alloc> allocations = new ArrayList<>();
    private List<PayableVO.AttachmentRef> attachments = new ArrayList<>();
    private LocalDateTime createTime;
    private String createBy;
    private LocalDateTime updateTime;
    private String updateBy;

    @Data
    public static class Alloc {
        private Long soId;
        private String soNo;
        private Long soItemId;
        private String model;
        private String brand;
        private BigDecimal amount;
        private BigDecimal amountCny;
    }
}
