package com.zhul.erp.modules.aftersales.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 退货去向：同一退货行可以拆到多个去向，合计等于实收 */
@Data
public class DisposeReturnRequest {
    @Valid
    @NotEmpty(message = "请填写去向")
    private List<Line> lines;

    @Data
    public static class Line {
        @NotNull(message = "请选择退货行")
        private Long itemId;
        /** 1-退回供应商、2-转闲置库存、3-报废 */
        @NotNull(message = "请选择去向")
        private Integer disposition;
        @NotNull(message = "请填写数量")
        private Integer quantity;
        /** 退回供应商时必填 */
        private Long supplierId;
        @Size(max = 64, message = "快递单号不能超过 64 字")
        private String trackingNo;
        /** 退回运费（CNY） */
        private BigDecimal freightCny;
        /** 供应商退款或抵扣（CNY） */
        private BigDecimal supplierRefundCny;
        @Size(max = 300, message = "说明不能超过 300 字")
        private String remark;
    }
}
