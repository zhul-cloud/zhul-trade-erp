package com.zhul.erp.modules.system.dto;

import lombok.Data;

/** 负责人等下拉用的用户选项：只有 ID 和姓名，不含手机号、邮箱（audit SEC-019） */
@Data
public class UserOptionVO {
    private Integer id;
    private String name;
}
