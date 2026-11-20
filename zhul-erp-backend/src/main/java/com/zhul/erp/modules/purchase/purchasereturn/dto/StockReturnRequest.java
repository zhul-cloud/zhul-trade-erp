package com.zhul.erp.modules.purchase.purchasereturn.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 库存退回：在库存查询选同一商品的若干批次退回一个供应商（spec purchase/purchase-return「库存退回」） */
@Data
public class StockReturnRequest {
    /** 供应商；线上店铺采购的采购订单没有供应商时填 0 并选采购订单 */
    @NotNull(message = "请选择供应商")
    private Long supplierId;
    /** 采购订单（默认批次的来源采购订单，可以不选） */
    private Long poId;
    @Size(max = 500, message = "备注最多 500 字")
    private String remark;
    @NotEmpty(message = "请选择要退回的批次")
    @Valid
    private List<Line> lines;

    @Data
    public static class Line {
        @NotNull(message = "请选择批次")
        private Long batchId;
        /** 1-合格、2-不良 */
        @NotNull(message = "请选择库存状态")
        private Integer stockStatus;
        @NotNull(message = "请填写数量")
        @Min(value = 1, message = "数量至少为 1")
        private Integer quantity;
    }
}
