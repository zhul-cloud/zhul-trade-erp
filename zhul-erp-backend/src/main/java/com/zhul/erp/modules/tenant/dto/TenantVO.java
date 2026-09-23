package com.zhul.erp.modules.tenant.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TenantVO {
    private Integer id;
    private String code;
    private String name;
    private Integer packageId;
    private String packageName;
    private String contactName;
    private String contactPhone;
    /** 租户管理员登录邮箱，取自关联的管理员账号（account.email），不存在 tenant 表上 */
    private String contactEmail;
    private Integer status;
    private LocalDateTime expireTime;
    private String remark;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
}
