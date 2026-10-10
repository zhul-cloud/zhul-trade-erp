package com.zhul.erp.modules.sales.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 手动创建订单：只填必填项（客户、币种、销售日期、型号 / 数量 / 单价） */
@Data
public class CreateOrderRequest {
    @NotNull(message = "请选择客户")
    private Long customerId;
    /** 不传时为 USD */
    private String currencyCode;
    /** 不传时为当天 */
    private LocalDate salesDate;
    @Size(max = 500, message = "备注不能超过 500 字")
    private String remark;
    @Valid
    private List<Line> items;

    @Data
    public static class Line {
        @Size(max = 128, message = "型号不能超过 128 个字符")
        private String model;
        @Size(max = 64, message = "品牌不能超过 64 个字符")
        private String brand;
        private Integer quantity;
        private BigDecimal unitPrice;
        /** 采购成本价（CNY），选填 */
        private BigDecimal costPrice;
        /** 采购员，选填 */
        private Long purchaserId;
        /** 1-现货、2-期货，默认现货 */
        private Integer stockType;
    }
}
