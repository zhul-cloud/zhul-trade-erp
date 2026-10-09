package com.zhul.erp.modules.logistics.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 出运单 */
@Data
public class LogisticsVO {
    private Long id;
    private String shNo;
    private Long customerId;
    private String customerName;
    private Long forwarderId;
    private String forwarderName;
    private Integer volumeDivisor;
    private Long ownerId;
    private String ownerName;
    private Integer status;
    private String statusName;
    private String carrier;
    private String waybillNo;
    private LocalDate shippedDate;
    private BigDecimal freight;
    private Boolean reconciled;
    private String voidReason;
    private String note;
    private Integer outboundCount;
    private List<SoRef> orders;
    private Integer boxCount;
    /** 按货代体积系数的计费重合计 */
    private BigDecimal chargeableWeight;
    /** 详情才有：出库单（箱上带国际运费分摊） */
    private List<OutboundVO> outbounds;
    /** 详情才有：箱号 → 分到的国际运费 */
    private java.util.Map<Long, BigDecimal> boxFreight;
    /** 详情才有：按货代体积系数的每箱计费重 */
    private java.util.Map<Long, BigDecimal> boxChargeable;
    private List<DirectRef> directs;
    private List<DocGroup> docGroups;
    private List<com.zhul.erp.modules.attachment.dto.AttachmentVO> faceSheets;
    private LocalDateTime createTime;
    private String createBy;
    private LocalDateTime updateTime;
    private String updateBy;

    @Data
    public static class SoRef {
        private Long id;
        private String soNo;
    }

    @Data
    public static class DocGroup {
        private Long id;
        private String ciNo;
        private String plNo;
        private List<String> soNos;
        private String paymentRef;
    }
}
