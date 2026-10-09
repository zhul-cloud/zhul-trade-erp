package com.zhul.erp.modules.purchase.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 采购单列表行 */
@Data
public class PurchaseOrderListVO {
    private Long id;
    /** 草稿为空 */
    private String poNo;
    private Integer status;
    private String statusName;
    private LocalDate orderDate;
    private LocalDateTime createTime;
    /** 草稿已放天数（超过 3 天时有值） */
    private Integer staleDays;
    private Long supplierId;
    /** 老供应商名称，或「淘宝 · 店铺名」 */
    private String supplierName;
    /** 采购对象为线上店铺 */
    private Boolean shop;
    private Integer channel;
    private String shopName;
    private Integer itemCount;
    private Integer totalQuantity;
    /** 还没填单价的行数 */
    private Integer missingPriceCount;
    private String currencyCode;
    private BigDecimal totalAmount;
    private BigDecimal totalAmountCny;
    private BigDecimal bargainAmount;
    /** 砍价率（%），没有目标价时为空 */
    private BigDecimal bargainRate;
    private String paymentTermsText;
    private Integer attachmentCount;
    private List<SoRef> orders;
    /** 来源订单已取消的行数 */
    private Integer orderCancelledCount;
    private Long purchaserId;
    private String purchaserName;
    private String cancelReason;
    private String createBy;
    private LocalDateTime updateTime;
    private String updateBy;

    @Data
    public static class SoRef {
        private Long id;
        private String soNo;
    }
}
