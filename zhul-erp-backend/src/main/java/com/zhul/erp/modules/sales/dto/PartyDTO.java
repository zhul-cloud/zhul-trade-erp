package com.zhul.erp.modules.sales.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 单证主体快照：买方或收货人（英文） */
@Data
public class PartyDTO {
    /** 来源单证主体ID（customer_party.id）；手填时为空 */
    private Long partyId;
    @NotBlank(message = "请填写公司名称")
    @Size(max = 200, message = "公司名称不能超过 200 个字符")
    private String name;
    @Size(max = 64, message = "国家不能超过 64 个字符")
    private String country;
    private String state;
    private String city;
    private String postcode;
    @Size(max = 300, message = "地址不能超过 300 个字符")
    private String address;
    @Size(max = 64, message = "税号不能超过 64 个字符")
    private String taxId;
    @Size(max = 64, message = "联系人不能超过 64 个字符")
    private String contact;
    @Size(max = 64, message = "电话不能超过 64 个字符")
    private String phone;
    @Size(max = 128, message = "邮箱不能超过 128 个字符")
    private String email;
}
