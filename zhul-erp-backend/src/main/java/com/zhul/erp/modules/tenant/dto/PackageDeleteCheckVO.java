package com.zhul.erp.modules.tenant.dto;

import lombok.Data;

@Data
public class PackageDeleteCheckVO {
    private boolean blocked;
    private Integer tenantCount;
}
