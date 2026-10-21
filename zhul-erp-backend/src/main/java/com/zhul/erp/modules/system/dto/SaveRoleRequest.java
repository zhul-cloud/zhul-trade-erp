package com.zhul.erp.modules.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class SaveRoleRequest {
    @NotBlank(message = "角色名称不能为空")
    private String name;
    private Integer permissionScope = 0;
    /** 工作台模板编码，不传时新建为通用、编辑不改 */
    private String workbenchTemplate;
    private Integer status = 1;
    private String remark;
    private List<Integer> deptIds;
}
