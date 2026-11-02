package com.zhul.erp.modules.masterdata.customerprofile.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 联系人与决策人画像 */
@Data
public class ContactVO {
    private Long id;
    private Boolean primary;
    private String name;
    private String title;
    private String email;
    private String phone;
    private String whatsapp;
    private String otherIm;
    private Integer jobRole;
    private Integer decisionRole;
    private Integer commStyle;
    private Integer disc;
    private Integer chinaAttitude;
    private String activeHours;
    private String likes;
    private LocalDateTime updateTime;
}
