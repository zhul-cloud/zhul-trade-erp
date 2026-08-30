package com.zhul.erp.modules.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhul.erp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("role")
public class RoleDO extends BaseEntity {
    private Integer tenantId;
    private String code;
    private String name;
    private Integer permissionScope;
    private Integer status;
    private Integer isBuiltIn;
    private String remark;
}
