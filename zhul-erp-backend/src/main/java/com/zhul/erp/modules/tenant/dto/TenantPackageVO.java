package com.zhul.erp.modules.tenant.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class TenantPackageVO {
    private Integer id;
    private String name;
    private String description;
    private List<Integer> menuIds;
    /** menuIds 的元素个数，展示用 */
    private Integer menuCount;
    /** 绑定该套餐的租户数 */
    private Integer tenantCount;
    private Integer status;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
}
