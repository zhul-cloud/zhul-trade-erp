package com.zhul.erp.modules.purchase.purchasereturn.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/** 寄出前修改数量（只能减少） */
@Data
public class PurchaseReturnQtyRequest {
    @NotEmpty(message = "请填写数量")
    @Valid
    private List<Line> items;

    @Data
    public static class Line {
        @NotNull
        private Long itemId;
        @NotNull
        private Integer quantity;
    }
}
