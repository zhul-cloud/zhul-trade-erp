package com.zhul.erp.modules.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateUserRequest {
    @NotBlank(message = "姓名不能为空")
    private String name;
    @NotBlank(message = "用户名不能为空")
    // 用户名记入各表的创建人 / 更新人（varchar(32)）
    @Size(max = 32, message = "用户名不能超过 32 个字")
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
