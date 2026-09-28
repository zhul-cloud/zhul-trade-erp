package com.zhul.erp.modules.masterdata.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 客户列表 / 选择器出参 */
@Data
public class CustomerVO {
    private Long id;
    private String customerCode;
    private String name;
    /** 展示名：有客户名称用名称，否则用联系人名称（经商机登记的客户可能暂缺名称） */
    private String displayName;
    /** 客户名称是否未填写 */
    private boolean nameMissing;
    private String nameCn;
    private String shortName;
    private String country;
    private Integer customerRole;
    private Integer customerGrade;
    private Integer sourceChannel;
    private Long ownerId;
    private String ownerName;
    private String contactName;
    private String contactPhone;
    private String contactEmail;
    private Integer status;
    private LocalDateTime createTime;
    private String createBy;
    private LocalDateTime updateTime;
    private String updateBy;
}
