package com.zhul.erp.modules.tenant.dto;

import lombok.Data;

/** 重置管理员密码的结果：临时密码只在这一次响应里明文返回 */
@Data
public class ResetPasswordResultVO {
    private String adminEmail;
    private String tempPassword;
}
