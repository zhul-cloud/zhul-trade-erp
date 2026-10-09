package com.zhul.erp.modules.warehouse.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 暂存货 */
@Data
public class HoldVO {
    private Long id;
    private String model;
    private String brand;
    private String category;
    private Integer quantity;
    private BigDecimal costPrice;
    private String locationNote;
    private Integer status;
    private String statusName;
    private Long receiptId;
    private String grNo;
    private Long poId;
    private String poNo;
    private String supplierName;
    private String returnCarrier;
    private String returnTrackingNo;
    private BigDecimal returnFreight;
    private String handledByName;
    private LocalDateTime handledAt;
    private String handleNote;
    private LocalDateTime createTime;
    private String createBy;
    private LocalDateTime updateTime;
    private String updateBy;
}
