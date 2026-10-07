package com.zhul.erp.modules.sales.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** PI 列表行 */
@Data
public class PiListVO {
    private Long id;
    private String piNo;
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
    private Integer receiptStatus;
    private String receiptStatusName;
    private Integer status;
    private String statusName;
    private Integer currentVersionNo;
    private Boolean revising;
    private List<String> quotationNos;
    private Long orderId;
    private String soNo;
    private Long ownerId;
    private String ownerName;
    private LocalDateTime createTime;
    private LocalDateTime sentAt;
}
