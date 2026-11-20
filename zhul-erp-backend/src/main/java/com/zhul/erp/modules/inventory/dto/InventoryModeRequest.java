package com.zhul.erp.modules.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 修改库存模式：dropship-无货源、stock-库存模式 */
@Data
public class InventoryModeRequest {
    @NotBlank(message = "请选择库存模式")
    private String mode;
}
