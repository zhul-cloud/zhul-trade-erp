package com.zhul.erp.modules.warehouse.dto;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 发货单列表行 */
@Data
public class ShipmentListVO {
    private Long id;
    private String sdNo;
    /** 直发货代 */
    private Long directForwarderId;
    private String directForwarderName;
    /** 直发货放进的出运单 */
    private Long logisticsId;
    private Long poId;
    private String poNo;
    private String supplierName;
    private Boolean shop;
    private String carrier;
    private String trackingNo;
    private LocalDate shipDate;
    private LocalDate expectedArrivalDate;
    /** 在途且已过预计到货日期 */
    private Boolean arrivalOverdue;
    private Integer itemCount;
    private Integer totalQuantity;
    private List<ShipmentItemVO> items;
    private Integer attachmentCount;
    private Integer source;
    private String sourceName;
    private Integer status;
    private String statusName;
    private String voidReason;
    private String note;
    private Long purchaserId;
    private String purchaserName;
    private Long receiptId;
    private String grNo;
    private LocalDateTime createTime;
    private String createBy;
    private LocalDateTime updateTime;
    private String updateBy;
}
