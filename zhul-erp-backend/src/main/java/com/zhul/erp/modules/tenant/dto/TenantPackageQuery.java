package com.zhul.erp.modules.tenant.dto;

import lombok.Data;

@Data
public class TenantPackageQuery {
    private Integer page = 1;
    private Integer pageSize = 20;
    private String name;
    private Integer status;
}
