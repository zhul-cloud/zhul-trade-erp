package com.zhul.erp.modules.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class SaveDictItemRequest {
    @NotNull(message = "所属字典类型不能为空")
    private Integer dictTypeId;
    @NotBlank(message = "字典项编码不能为空")
    @Pattern(regexp = "^[A-Z_]{2,64}$", message = "编码仅支持大写字母和下划线，长度2~64位")
    private String itemCode;
    @NotBlank(message = "字典项名称不能为空")
    private String itemName;
    @NotBlank(message = "字典值不能为空")
    private String itemValue;
    private String cssClass;
    private Integer sortOrder = 0;
    private Integer isDefault = 0;
    private Integer status = 1;
    private String remark;
}
