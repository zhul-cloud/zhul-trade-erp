package com.zhul.erp.modules.masterdata.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 客户列表 / 选择器出参 */
@Data
public class CustomerVO {
    private Long id;
    private String customerCode;
    private String name;
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
