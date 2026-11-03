package com.zhul.erp.modules.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.ToString;

/** 管理员重置密码：新密码放请求体，不进 URL，避免出现在访问日志里 */
@Data
public class AdminResetPasswordRequest {
    @NotBlank(message = "新密码不能为空")
    @ToString.Exclude
    private String newPassword;
}
