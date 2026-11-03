package com.zhul.erp.modules.masterdata.customerprofile.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 新增或修改联系人 */
@Data
public class ContactRequest {
    @NotBlank(message = "请填写联系人姓名")
    @Size(max = 100, message = "姓名不能超过 100 字")
    private String name;
    @Size(max = 50, message = "职位不能超过 50 字")
    private String title;
    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱不能超过 100 字")
    private String email;
    @Pattern(regexp = "^$|^[+0-9 ()\\-]{0,30}$", message = "电话格式不正确")
    private String phone;
    @Pattern(regexp = "^$|^[+0-9 ()\\-]{0,30}$", message = "WhatsApp 格式不正确")
    private String whatsapp;
    @Size(max = 100, message = "其他联系方式不能超过 100 字")
    private String otherIm;
    private Integer jobRole;
    private Integer decisionRole;
    private Integer commStyle;
    private Integer disc;
    private Integer chinaAttitude;
    @Size(max = 50, message = "活跃时间不能超过 50 字")
    private String activeHours;
    @Size(max = 300, message = "禁忌与喜好不能超过 300 字")
    private String likes;
    /** 设为主联系人 */
    private Boolean primary;
}
