package com.zhul.erp.modules.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhul.erp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("resource")
public class ResourceDO extends BaseEntity {
    private Integer pid;
    private Integer tenantId;
    private String code;
    private String name;
    private Integer type;   // 1-目录 2-菜单 3-按钮
    private Integer sort;
    private String lightIcon;
    private String lightSelectedIcon;
    private String darkIcon;
    private String darkSelectedIcon;
    private String path;
    private String componentPath;
    private String permission;
    private Integer status;
    private String microApp;
    private Integer isExternal;
    private Integer isCache;
    private Integer isHidden;
}
