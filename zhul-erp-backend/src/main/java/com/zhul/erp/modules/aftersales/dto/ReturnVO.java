package com.zhul.erp.modules.aftersales.dto;

import com.zhul.erp.modules.attachment.dto.AttachmentVO;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 退货单 */
@Data
public class ReturnVO {
    private Long id;
    private String returnNo;
    private Long afterSalesId;
    private String asNo;
    private Long soId;
    private String soNo;
    private String customerName;
    /** 客户国家/地区、新老客户、主联系人（列表客户列三行显示） */
    private String customerCountry;
    private Integer customerType;
    private String customerContact;
    private String carrier;
    private String trackingNo;
    private String remark;
    private Integer status;
    private String statusName;
    private LocalDate receivedDate;
    private String receivedByName;
    private String receiveNote;
    private String voidReason;
    private String itemSummary;
    private List<Item> items;
    private List<Disposition> dispositions;
    private List<AttachmentVO> attachments;
    /** 冲减原订单成本合计与退回运费（CNY） */
    private BigDecimal costCny;
    private BigDecimal freightCny;
    private LocalDateTime createTime;
    private String createBy;
    private LocalDateTime updateTime;
    private String updateBy;

    @Data
    public static class Item {
        private Long id;
        private Long soItemId;
        private String model;
        private String brand;
        private Integer quantity;
        private Integer receivedQty;
        private Integer qualifiedQty;
        /** 原采购成本（CNY / 个），转闲置库存按它冲减 */
        private BigDecimal unitCost;
        /** 默认退回的供应商（原采购订单） */
        private Long supplierId;
        private String supplierName;
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
        private String trackingNo;
        private BigDecimal freightCny;
        private BigDecimal supplierRefundCny;
        private BigDecimal costCny;
        private Long holdId;
        private String remark;
        private String operatorName;
        private LocalDateTime createTime;
    }
}
