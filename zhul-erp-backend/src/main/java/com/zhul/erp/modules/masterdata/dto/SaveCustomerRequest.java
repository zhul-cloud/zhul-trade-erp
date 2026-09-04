package com.zhul.erp.modules.masterdata.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SaveCustomerRequest {
    @NotBlank(message = "客户名称不能为空")
    private String name;
    private String country;
    private String contactName;
    private String contactPhone;
    private String contactEmail;
    /** 名称与已有客户重复时，是否仍然强制新建（默认 false：先返回重复提示，不新建） */
    private boolean force = false;
}
