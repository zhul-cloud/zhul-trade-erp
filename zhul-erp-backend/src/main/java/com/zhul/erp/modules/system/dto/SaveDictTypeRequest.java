package com.zhul.erp.modules.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class SaveDictTypeRequest {
    @NotBlank(message = "字典类型编码不能为空")
    @Pattern(regexp = "^[a-z_]{2,64}$", message = "编码仅支持小写字母和下划线，长度2~64位")
    private String dictType;
    @NotBlank(message = "字典类型名称不能为空")
    private String dictName;
    private Integer status = 1;
    private String remark;
}
