package com.zhul.erp.modules.aftersales.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 退货去向：同一行可以拆到多个去向，可以分几次处理，累计不超过实收；退回供应商的快递、运费与退款在采购退货单上登记 */
@Data
public class DisposeReturnRequest {
    @Valid
    @NotEmpty(message = "请填写去向")
    private List<Line> lines;

    @Data
    public static class Line {
        /** 退货入库单行 ID */
        @NotNull(message = "请选择退货行")
        private Long itemId;
        /** 1-退回供应商、2-转闲置库存、3-报废 */
        @NotNull(message = "请选择去向")
        private Integer disposition;
        @NotNull(message = "请填写数量")
        private Integer quantity;
        /** 退回供应商时必填（默认原采购订单的供应商） */
        private Long supplierId;
        /** 退回供应商：关联的采购订单（默认原订单型号行的采购订单，可以不选） */
        private Long poId;
        @Size(max = 300, message = "说明不能超过 300 字")
        private String remark;
    }
}
