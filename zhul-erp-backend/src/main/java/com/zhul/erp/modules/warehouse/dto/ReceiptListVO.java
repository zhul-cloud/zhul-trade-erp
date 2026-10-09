package com.zhul.erp.modules.warehouse.dto;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 入库单列表行 */
@Data
public class ReceiptListVO {
    private Long id;
    private String grNo;
    private LocalDate receivedDate;
    private Long shipmentId;
    private String sdNo;
    private Long poId;
    private String poNo;
    private String supplierName;
    private Integer itemCount;
    private Integer receivedQty;
    private Integer qualifiedQty;
    private Integer defectiveQty;
    private Integer discrepancyCount;
    private String receivedByName;
    private Integer status;
    private String statusName;
    private String reverseReason;
    private String note;
    private LocalDateTime createTime;
    private String createBy;
    private LocalDateTime updateTime;
    private String updateBy;
}
