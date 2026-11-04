package com.zhul.erp.modules.crm.followup.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新建开发对象（第四期 design D3）：公司名称、国家、为什么值得开发必填；联系人选填。
 * 客户已存在时返回 CUSTOMER_DUPLICATE（detail 带已有客户），前端可改为把已有客户加入开发。
 */
@Data
public class ProspectRequest {
    @NotBlank(message = "请填写公司名称")
    @Size(max = 200, message = "公司名称不能超过200个字")
    private String name;
    @NotBlank(message = "请选择国家")
    @Size(max = 64, message = "国家不正确")
    private String country;
    @Size(max = 200, message = "官网不能超过200个字")
    private String website;
    @NotBlank(message = "请写为什么值得开发")
    @Size(max = 200, message = "为什么值得开发不能超过200个字")
    private String reason;
    /** 1-Google、2-LinkedIn、3-展会名录、4-海关数据、5-老客户介绍、6-其他 */
    private Integer source;
    @Size(max = 100, message = "联系人姓名不能超过100个字")
    private String contactName;
    @Size(max = 100, message = "邮箱不能超过100个字")
    private String email;
    @Size(max = 30, message = "WhatsApp 不能超过30个字")
    private String whatsapp;
    /** 所属事业部：负责人不属于事业部、公司有多个事业部时必填（与商机登记一致） */
    private Integer businessUnitId;
}
