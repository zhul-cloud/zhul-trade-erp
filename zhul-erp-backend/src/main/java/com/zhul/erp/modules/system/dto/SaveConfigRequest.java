package com.zhul.erp.modules.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class SaveConfigRequest {
    @NotBlank(message = "配置键不能为空")
    @Pattern(regexp = "^[a-z][a-z0-9_.]{3,127}$", message = "配置键仅支持小写字母、数字、点和下划线，长度4~128位")
    private String configKey;
    @NotBlank(message = "配置名称不能为空")
    private String configName;
    @NotBlank(message = "配置值不能为空")
    private String configValue;
    @NotBlank(message = "配置类型不能为空")
    @Pattern(regexp = "^(STRING|INTEGER|BOOLEAN|JSON|URL)$", message = "配置类型不合法")
    private String configType;
    @NotBlank(message = "配置分组不能为空")
    private String configGroup;
    private Integer isEncrypted = 0;
    private String remark;
}
