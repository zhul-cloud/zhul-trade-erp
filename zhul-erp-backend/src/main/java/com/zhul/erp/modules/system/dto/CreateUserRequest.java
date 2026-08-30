package com.zhul.erp.modules.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateUserRequest {
    @NotBlank(message = "姓名不能为空")
    private String name;
    @NotBlank(message = "用户名不能为空")
    private String username;
    @NotBlank(message = "手机号不能为空")
    private String phone;
    private String email;
    private String nickname;
    private Integer deptId;
    private Integer positionId;
    private String roleCode;
    @NotBlank(message = "初始密码不能为空")
    private String password;
    private Integer status = 1;
}
