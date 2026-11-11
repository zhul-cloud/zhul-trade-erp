package com.zhul.erp.modules.purchase.purchasereturn.dto;

import com.zhul.erp.modules.attachment.dto.AttachmentVO;
import com.zhul.erp.modules.system.doclog.DocLogVO;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 采购退货单（列表与详情） */
@Data
public class PurchaseReturnVO {
    private Long id;
    private String returnNo;
    private Long supplierId;
    private String supplierName;
    private Integer sourceType;
    private String sourceTypeName;
    /** 来源单号：售后退货为退货入库单号，到货差异为采购入库单号 */
    private Long sourceId;
    private String sourceNo;
    private Long poId;
    private String poNo;
    private Long afterSalesId;
    private String asNo;
    private Long soId;
    private String soNo;
    private String customerName;
    private String customerCountry;
    private Long ownerId;
    private String ownerName;
    private Integer status;
    private String statusName;
    private LocalDate shipDate;
    private String carrier;
    private String trackingNo;
    private BigDecimal freightCny;
    private Long payerId;
    private String payerName;
    private LocalDate signedDate;
    private String currencyCode;
    private BigDecimal exchangeRate;
    private BigDecimal refundAmount;
    private BigDecimal refundCny;
    private String settleNote;
    private String remark;
    private String voidReason;
    private Boolean migrated;
    private String itemSummary;
    private Integer totalQty;
    private List<String> brands;
    private List<String> categories;
    private List<Item> items;
    private List<AttachmentVO> attachments;
    private List<AttachmentVO> freightProofs;
    private List<DocLogVO> logs;
    private LocalDateTime createTime;
    private String createBy;
    private LocalDateTime updateTime;
    private String updateBy;

    @Data
    public static class Item {
        private Long id;
        private String model;
        private String brand;
        private String category;
        private Integer quantity;
        private Long soItemId;
        private Long poItemId;
        private Long receiptItemId;
        private Long discrepancyId;
        private String discrepancyName;
    }
}
