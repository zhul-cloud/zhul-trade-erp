package com.zhul.erp.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VerifyResetCodeRequest {
    @NotBlank(message = "请输入用户名")
    private String username;
    @NotBlank(message = "请输入验证码")
    private String code;
}
