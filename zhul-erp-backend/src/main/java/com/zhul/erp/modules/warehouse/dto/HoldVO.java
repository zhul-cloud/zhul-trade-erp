package com.zhul.erp.modules.warehouse.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 闲置库存 */
@Data
public class HoldVO {
    private Long id;
    /** 来源（1-到货差异、2-订单部分取消） */
    private Integer sourceType;
    private String sourceName;
    /** 来源销售订单（订单部分取消） */
    private Long soId;
    private String soNo;
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
