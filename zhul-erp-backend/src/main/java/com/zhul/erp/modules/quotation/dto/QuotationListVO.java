package com.zhul.erp.modules.quotation.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 报价单列表行 */
@Data
public class QuotationListVO {
    private Long id;
    private String quotationNo;
    private Long customerId;
    private String customerName;
    /** 客户国家 */
    private String customerCountry;
    /** 1-新客户、2-老客户（按来源客户询盘判断） */
    private Integer customerType;
    /** 总数量：全部型号数量之和 */
    private Integer totalQuantity;
    private Integer itemCount;
    private String currencyCode;
    private BigDecimal totalAmount;
    private BigDecimal marginRate;
    private Integer status;
    private String statusName;
    private String lostReasonName;
    private List<String> inquiryCodes;
    private Long ownerId;
    private String ownerName;
    private LocalDateTime createTime;
    private LocalDateTime sentAt;
    /** 当前版本号 */
    private Integer currentVersionNo;
    /** 修改中的新版本号，没有时为空 */
    private Integer editingVersionNo;
}
