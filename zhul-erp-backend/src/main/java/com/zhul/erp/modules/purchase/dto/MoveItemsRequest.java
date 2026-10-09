package com.zhul.erp.modules.purchase.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/** 草稿行改到其他供应商 */
@Data
public class MoveItemsRequest {
    @NotEmpty(message = "请选择型号")
    private List<Long> itemIds;
    @NotNull(message = "请选择供应商")
    private Long supplierId;
}
