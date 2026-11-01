package com.zhul.erp.modules.aftersales.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 登记售后单 */
@Data
public class SaveAfterSalesRequest {
    @NotNull(message = "请选择客户")
    private Long customerId;
    /** 选填：没有订单号时为空 */
    private Long soId;
    @Valid
    private List<Line> items;
    /** 字典 after_sales_level，空为「待确认」 */
    private String levelCode;
    @NotBlank(message = "请填写问题现象")
    @Size(max = 1000, message = "问题现象不能超过 1000 字")
    private String symptom;
    @Size(max = 500, message = "客户诉求不能超过 500 字")
    private String customerRequest;
    private LocalDateTime receivedAt;
    /** 为空时默认订单业务员，没有订单时为登记人 */
    private Long ownerId;
    @Size(max = 300, message = "下一步行动不能超过 300 字")
    private String nextAction;
    private LocalDateTime nextUpdateAt;
    private List<Long> attachmentIds;

    @Data
    public static class Line {
        /** 从订单型号选时填写 */
        private Long soItemId;
        @Size(max = 128, message = "型号不能超过 128 字")
        private String model;
        @Size(max = 64, message = "品牌不能超过 64 字")
        private String brand;
        @NotNull(message = "请填写数量")
        private Integer quantity;
    }
}
