package com.zhul.erp.modules.warehouse.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 到货差异 */
@Data
public class DiscrepancyVO {
    private Long id;
    private Long receiptId;
    private String grNo;
    private LocalDate receivedDate;
    private Long poId;
    private String poNo;
    private String supplierName;
    private String currencyCode;
    private String model;
    private String brand;
    private String category;
    private Integer type;
    private String typeName;
    private Integer quantity;
    private Integer status;
    private String statusName;
    private Integer resolution;
    private String resolutionName;
    private BigDecimal discountAmount;
    private String returnCarrier;
    private String returnTrackingNo;
    private BigDecimal returnFreight;
    private Boolean freeOfCharge;
    private String note;
    private Long purchaserId;
    private String purchaserName;
    private String handledByName;
    private LocalDateTime handledAt;
    private Long holdId;
    private LocalDateTime createTime;
    private String createBy;
    private LocalDateTime updateTime;
    private String updateBy;
}
