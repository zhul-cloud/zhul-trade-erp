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
