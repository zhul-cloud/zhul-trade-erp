package com.zhul.erp.modules.system.dto;

import lombok.Data;
import java.util.List;

@Data
public class MenuVO {
    private Integer id;
    private Integer pid;
    private String code;
    private String name;
    private Integer type;
    private Integer sort;
    private String path;
    private String componentPath;
    private String permission;
    private String lightIcon;
    private String darkIcon;
    private String microApp;
    private Integer isExternal;
    private Integer isCache;
    private Integer isHidden;
    private Integer status;
    private List<MenuVO> children;
}
