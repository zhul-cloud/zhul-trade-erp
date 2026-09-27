package com.zhul.erp.modules.masterdata.dto;

import lombok.Data;

/** 客户引用信息：跨模块回显名称用，不按数据权限过滤，所以只含不敏感的字段 */
@Data
public class CustomerRefVO {
    private Long id;
    private String customerCode;
    private String name;
    private String country;
    private Integer status;
}
