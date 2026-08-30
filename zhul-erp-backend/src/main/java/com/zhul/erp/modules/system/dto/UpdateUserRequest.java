package com.zhul.erp.modules.system.dto;

import lombok.Data;

@Data
public class UpdateUserRequest {
    private String name;
    private String phone;
    private String email;
    private String nickname;
    private Integer deptId;
    private Integer positionId;
    private String roleCode;
    private Integer status;
}
