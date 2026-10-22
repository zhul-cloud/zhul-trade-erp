package com.zhul.erp.modules.system.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class RoleVO {
    private Integer id;
    private String code;
    private String name;
    private Integer permissionScope;
    /** 工作台模板编码 */
    private String workbenchTemplate;
    private Integer status;
    private Integer isBuiltIn;
    private Integer userCount;
    private String remark;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
}
