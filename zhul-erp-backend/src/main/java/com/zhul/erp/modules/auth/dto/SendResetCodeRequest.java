package com.zhul.erp.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class SendResetCodeRequest {
    @NotBlank(message = "请输入用户名")
    private String username;
    @NotBlank(message = "请输入邮箱地址")
    @Pattern(regexp = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", message = "邮箱格式不正确")
    private String email;
}
