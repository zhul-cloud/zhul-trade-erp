package com.zhul.erp.modules.crm.followup.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 把已有客户加入主动开发（第四期 design D2）：要写为什么值得开发，来源方式选填 */
@Data
public class JoinDevelopRequest {
    @NotBlank(message = "请写为什么值得开发")
    @Size(max = 200, message = "为什么值得开发不能超过200个字")
    private String reason;
    private Integer source;
}
