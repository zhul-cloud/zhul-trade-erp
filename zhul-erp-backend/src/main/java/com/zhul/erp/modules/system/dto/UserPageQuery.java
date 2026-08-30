package com.zhul.erp.modules.system.dto;

import lombok.Data;

@Data
public class UserPageQuery {
    private Integer page = 1;
    private Integer pageSize = 10;
    private String name;
    private String phone;
    private Integer deptId;
    private Integer status;
}
