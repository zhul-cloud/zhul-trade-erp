package com.zhul.erp.modules.masterdata.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CustomerVO {
    private Long id;
    private String name;
    private String country;
    private String contactName;
    private String contactPhone;
    private String contactEmail;
    private Integer status;
    private LocalDateTime createTime;
}
