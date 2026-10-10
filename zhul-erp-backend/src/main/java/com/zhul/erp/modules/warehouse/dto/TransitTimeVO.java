package com.zhul.erp.modules.warehouse.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 快递时效规则 */
@Data
public class TransitTimeVO {
    private Long id;
    private String carrier;
    /** 空表示该快递公司的默认天数 */
    private String originProvince;
    private Integer days;
    private String remark;
    private LocalDateTime createTime;
    private String createBy;
    private LocalDateTime updateTime;
    private String updateBy;
}
