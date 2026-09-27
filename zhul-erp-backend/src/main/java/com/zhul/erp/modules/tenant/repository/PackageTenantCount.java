package com.zhul.erp.modules.tenant.repository;

import lombok.Data;

/** 按套餐 ID 分组统计的绑定租户数。 */
@Data
public class PackageTenantCount {
    private Integer id;
    private Integer cnt;
}
