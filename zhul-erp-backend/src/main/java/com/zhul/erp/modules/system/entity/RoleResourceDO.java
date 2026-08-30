package com.zhul.erp.modules.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhul.erp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("role_resource")
public class RoleResourceDO extends BaseEntity {
    private String roleCode;
    private Integer resourceId;
    private String resourceCode;
}
