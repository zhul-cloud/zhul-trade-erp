package com.zhul.erp.modules.masterdata.dto;

import lombok.Data;

/** 客户引用信息：跨模块回显名称用，不按数据权限过滤，所以只含不敏感的字段 */
@Data
public class CustomerRefVO {
    private Long id;
    private String customerCode;
    private String name;
    /** 展示名：有客户名称用名称，否则用联系人名称（经商机登记的客户可能暂缺名称） */
    private String displayName;
    /** 客户名称是否未填写 */
    private boolean nameMissing;
    private String country;
    private Integer status;
}
