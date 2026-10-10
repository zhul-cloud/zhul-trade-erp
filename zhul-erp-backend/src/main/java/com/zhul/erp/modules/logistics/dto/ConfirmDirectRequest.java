package com.zhul.erp.modules.logistics.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 按货代实收确认直发货：实收数量与箱规 */
@Data
public class ConfirmDirectRequest {
    @NotEmpty
    @Valid
    private List<Line> lines;
    @NotEmpty(message = "请登记货代量的箱规")
    @Valid
    private List<BoxLine> boxes;

    @Data
    public static class Line {
        @NotNull
        private Long shipmentItemId;
        @NotNull(message = "请填写实收数量")
        @Min(value = 0, message = "实收数量不能为负数")
        private Integer quantity;
    }

    @Data
    public static class BoxLine {
        @NotNull(message = "请填写长")
        @Min(value = 1, message = "尺寸须大于 0")
        private Integer length;
        @NotNull(message = "请填写宽")
        @Min(value = 1, message = "尺寸须大于 0")
        private Integer width;
        @NotNull(message = "请填写高")
        @Min(value = 1, message = "尺寸须大于 0")
        private Integer height;
        @NotNull(message = "请填写毛重")
        @DecimalMin(value = "0.01", message = "毛重须大于 0")
        @Digits(integer = 8, fraction = 2, message = "重量最多两位小数")
        private BigDecimal grossWeight;
        @DecimalMin(value = "0.01", message = "净重须大于 0")
        @Digits(integer = 8, fraction = 2, message = "重量最多两位小数")
        private BigDecimal netWeight;
        @NotEmpty(message = "箱子里没有型号")
        @Valid
        private List<BoxItem> items;
    }

    @Data
    public static class BoxItem {
        @NotNull
        private Long shipmentItemId;
        @NotNull(message = "请填写数量")
        @Min(value = 1, message = "箱内数量须大于 0")
        private Integer quantity;
    }
}
