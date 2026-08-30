package com.zhul.erp.modules.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class SavePositionRequest {
    @NotBlank(message = "岗位编码不能为空")
    @Pattern(regexp = "^[A-Z0-9_]{2,32}$", message = "编码仅支持大写字母、数字和下划线，长度2~32位")
    private String code;
    @NotBlank(message = "岗位名称不能为空")
    private String name;
    private Integer sort = 0;
    private Integer status = 1;
    private String remark;
}
