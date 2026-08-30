package com.zhul.erp.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ResetPasswordRequest {
    @NotBlank(message = "重置凭证缺失")
    private String verifyToken;
    @NotBlank(message = "请输入新密码")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,32}$", message = "密码需为8-32位且同时包含字母和数字")
    private String newPassword;
    @NotBlank(message = "请再次输入密码")
    private String confirmPassword;
}
