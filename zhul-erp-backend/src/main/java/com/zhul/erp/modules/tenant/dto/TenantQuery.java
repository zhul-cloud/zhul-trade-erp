package com.zhul.erp.modules.tenant.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class TenantQuery {
    private Integer page = 1;
    private Integer pageSize = 20;
    /** 按租户名称模糊匹配 */
    private String name;
    private Integer status;
    /** 按行业筛选 */
    private String industryCode;
    /** 到期时间范围筛选：起（含） */
    private LocalDate expireDateFrom;
    /** 到期时间范围筛选：止（含） */
    private LocalDate expireDateTo;
}
