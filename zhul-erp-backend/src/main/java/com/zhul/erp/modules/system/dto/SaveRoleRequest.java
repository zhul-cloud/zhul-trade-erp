package com.zhul.erp.modules.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class SaveRoleRequest {
    @NotBlank(message = "角色名称不能为空")
    private String name;
    /** 数据权限范围（1-全部、2-自定义部门、3-仅本人）；编辑时不传表示不改，新建不传按仅本人 */
    private Integer permissionScope;
    /** 工作台模板编码，不传时新建为通用、编辑不改 */
    private String workbenchTemplate;
    /** 状态（0-禁用、1-启用）；编辑时不传表示不改，新建不传为启用 */
    private Integer status;
    private String remark;
    private List<Integer> deptIds;
}
