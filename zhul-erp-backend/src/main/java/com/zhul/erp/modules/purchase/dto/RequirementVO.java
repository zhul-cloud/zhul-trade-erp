package com.zhul.erp.modules.purchase.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 采购需求列表行 */
@Data
public class RequirementVO {
    private Long id;
    private Long soId;
    private String soNo;
    private LocalDate salesDate;
    private String customerName;
    private String customerCountry;
    private String model;
    private String brand;
    private Integer quantity;
    private Integer draftQty;
    private Integer orderedQty;
    private Integer availableQty;
    /** 目标价（CNY，不含税），为空表示没有 */
    private BigDecimal targetPrice;
    private Long purchaserId;
    private String purchaserName;
    private Long suggestedSupplierId;
    private String suggestedSupplierName;
    private Integer suggestedChannel;
    private String suggestedChannelName;
    private String suggestedShopName;
    /** 1-现货、2-期货 */
    private Integer stockType;
    /** pending / draft / partial / ordered / closed / orderCancelled */
    private String status;
    private String statusName;
    /** 所在采购单（草稿与已下单） */
    private List<PoRef> purchaseOrders;

    @Data
    public static class PoRef {
        private Long id;
        private String poNo;
        private Integer status;
        private String supplierName;
        private String purchaserName;
        private Integer quantity;
    }
}
