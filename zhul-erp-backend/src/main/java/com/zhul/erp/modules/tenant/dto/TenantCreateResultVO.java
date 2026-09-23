package com.zhul.erp.modules.tenant.dto;

import lombok.Data;

/**
 * 创建租户的结果：除了新建的租户信息，还带管理员账号的临时密码——
 * 密码只在这一次响应里明文返回，后端不再存储明文，前端要用不会自动消失的
 * 提示常驻展示，提醒平台管理员当场记录告知（见 PRD 5.2.3）。
 */
@Data
public class TenantCreateResultVO {
    private TenantVO tenant;
    private String adminEmail;
    private String tempPassword;
}
