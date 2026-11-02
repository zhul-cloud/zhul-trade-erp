package com.zhul.erp.modules.aftersales.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 售后补发（spec sales/after-sales「发起补发」） */
@Data
public class ReplacementRequest {
    @Valid
    private List<Line> items;
    @Size(max = 300, message = "说明不能超过 300 字")
    private String remark;

    @Data
    public static class Line {
        /** 原订单型号行；手填配件时为空 */
        private Long soItemId;
        @Size(max = 128, message = "型号不能超过 128 字")
        private String model;
        @Size(max = 64, message = "品牌不能超过 64 字")
        private String brand;
        @NotNull(message = "请填写数量")
        private Integer quantity;
        /** 申报单价（原币）；原订单型号为空时取原单价 */
        private BigDecimal declaredPrice;
        private Long purchaserId;
    }
}
