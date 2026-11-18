package com.zhul.erp.modules.sales.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 「再下一单」：按原订单预填新建手动订单的内容（订单号、日期、收款、进度都不带） */
@Data
public class ReorderDraftVO {
    private Long sourceId;
    private String sourceSoNo;
    private Long customerId;
    private String customerName;
    private String customerCountry;
    private String currencyCode;
    private List<Line> items;
    private List<Fee> fees;

    @Data
    public static class Line {
        /** 可选的商品档案；为空表示这个型号还没有建档（或已停用），要先建档才能下单 */
        private Long productId;
        private String mpnDisplay;
        private String brandName;
        private String categoryName;
        /** 原订单行的型号与品牌（没有商品档案时显示） */
        private String model;
        private String brand;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal costPrice;
        private Long purchaserId;
        private Integer stockType;
    }

    @Data
    public static class Fee {
        private String feeName;
        private BigDecimal amount;
    }
}
