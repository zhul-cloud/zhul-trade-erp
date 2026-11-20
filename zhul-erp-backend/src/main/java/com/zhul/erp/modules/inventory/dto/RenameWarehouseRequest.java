package com.zhul.erp.modules.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 自有仓改名 */
@Data
public class RenameWarehouseRequest {
    @NotBlank(message = "请填写仓库名称")
    @Size(max = 64, message = "仓库名称不能超过 64 个字")
    private String name;
}
