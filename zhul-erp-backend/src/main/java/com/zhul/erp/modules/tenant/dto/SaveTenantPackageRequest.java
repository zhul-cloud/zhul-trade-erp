package com.zhul.erp.modules.tenant.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class SaveTenantPackageRequest {
    @NotBlank(message = "请输入套餐名称")
    @Size(min = 2, max = 100, message = "套餐名称长度需在2~100字符之间")
    private String name;

    @Size(max = 500, message = "套餐描述最长500字符")
    private String description;

    @NotEmpty(message = "请至少选择一个菜单")
    private List<Integer> menuIds;
}
