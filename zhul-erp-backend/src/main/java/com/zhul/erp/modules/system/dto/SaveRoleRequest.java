package com.zhul.erp.modules.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class SaveRoleRequest {
    @NotBlank(message = "角色名称不能为空")
    private String name;
    private Integer permissionScope = 0;
    private Integer status = 1;
    private String remark;
    private List<Integer> deptIds;
}
