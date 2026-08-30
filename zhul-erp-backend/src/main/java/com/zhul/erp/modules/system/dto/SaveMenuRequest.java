package com.zhul.erp.modules.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SaveMenuRequest {
    private Integer pid = 0;
    @NotBlank(message = "菜单名称不能为空")
    private String name;
    @NotNull(message = "菜单类型不能为空")
    private Integer type;  // 1-目录 2-菜单 3-按钮
    private String path;
    private String componentPath;
    private String permission;
    private String lightIcon;
    private String darkIcon;
    private Integer sort = 0;
    private Integer status = 1;
    private String microApp;
    private Integer isExternal = 0;
    private Integer isCache = 1;
    private Integer isHidden = 0;
}
