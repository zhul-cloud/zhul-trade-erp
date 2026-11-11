package com.zhul.erp.modules.aftersales.dto;

import com.zhul.erp.modules.attachment.dto.AttachmentVO;
import com.zhul.erp.modules.system.doclog.DocLogVO;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 退货入库单 RR（列表与详情） */
@Data
public class ReturnReceiptVO {
    private Long id;
    private String receiptNo;
    private Long salesReturnId;
    private String returnNo;
    private Long afterSalesId;
    private String asNo;
    private Long soId;
    private String soNo;
    private String customerName;
    private String customerCountry;
    private Integer customerType;
    private String customerContact;
    private String carrier;
    private String trackingNo;
    private Long ownerId;
    private String ownerName;
    private Integer status;
    private String statusName;
    private LocalDate receivedDate;
    private String receivedByName;
    private String receiveNote;
    private String remark;
    private String voidReason;
    private String itemSummary;
    private Integer totalQty;
    private Integer receivedQty;
    private Integer qualifiedQty;
    private List<String> brands;
    private List<String> categories;
    private List<Item> items;
    private List<Disposition> dispositions;
    /** 由它生成的采购退货单 */
    private List<ReturnVO.DocBrief> purchaseReturns;
    private List<AttachmentVO> attachments;
    private List<DocLogVO> logs;
    /** 转闲置冲减与退回供应商冲减合计（CNY）、退回运费 */
    private BigDecimal costCny;
    private BigDecimal freightCny;
    private LocalDateTime createTime;
    private String createBy;
    private LocalDateTime updateTime;
    private String updateBy;

    @Data
    public static class Item {
        private Long id;
        private Long returnItemId;
        private Long soItemId;
        private String model;
        private String brand;
        private String category;
        private Integer quantity;
        private Integer receivedQty;
        private Integer qualifiedQty;
        private Integer disposedQty;
        private String note;
        /** 原采购成本（CNY / 个），转闲置库存按它冲减 */
        private BigDecimal unitCost;
        /** 默认退回的供应商与采购订单（原订单型号行的采购订单） */
        private Long supplierId;
        private String supplierName;
        private Long poId;
        private String poNo;
    }

    @Data
    public static class Disposition {
        private Long id;
        private Long itemId;
        private String model;
        private Integer disposition;
        private String dispositionName;
        private Integer quantity;
        private Long supplierId;
        private String supplierName;
        private BigDecimal freightCny;
        private BigDecimal costCny;
        private Long holdId;
        private Long purchaseReturnId;
        private String purchaseReturnNo;
        private String remark;
        private String operatorName;
        private LocalDateTime createTime;
    }
}
