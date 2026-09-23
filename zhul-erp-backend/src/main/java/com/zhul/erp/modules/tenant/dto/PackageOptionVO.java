package com.zhul.erp.modules.tenant.dto;

import lombok.Data;

/** 新增/编辑租户时套餐下拉选项：只展示启用中的套餐 */
@Data
public class PackageOptionVO {
    private Integer id;
    private String name;
}
