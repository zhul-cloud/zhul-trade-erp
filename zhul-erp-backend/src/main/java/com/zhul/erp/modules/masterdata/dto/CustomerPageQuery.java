package com.zhul.erp.modules.masterdata.dto;

import lombok.Data;

@Data
public class CustomerPageQuery {
    private Integer page = 1;
    private Integer pageSize = 10;
    /** 名称关键词（旧参数，保留兼容），同 name */
    private String keyword;
    private String customerCode;
    /** 模糊匹配英文名称、中文名称、简称 */
    private String name;
    private String country;
    private Integer customerRole;
    private Integer customerGrade;
    private Integer sourceChannel;
    private Long ownerId;
    private Integer status;
}
