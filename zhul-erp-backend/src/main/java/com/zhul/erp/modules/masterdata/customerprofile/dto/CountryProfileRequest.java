package com.zhul.erp.modules.masterdata.customerprofile.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 新增或修改国家资料 */
@Data
public class CountryProfileRequest {
    @NotBlank(message = "请选择国家")
    private String country;
    private List<Integer> voltages;
    @Size(max = 100, message = "插头类型不能超过 100 字")
    private String plug;
    @Size(max = 500, message = "重大节假日不能超过 500 字")
    private String holidays;
    @Min(value = 0, message = "外汇管制不正确")
    @Max(value = 3, message = "外汇管制不正确")
    private Integer fxControl;
    @Size(max = 500, message = "管制说明不能超过 500 字")
    private String fxNote;
    @Size(max = 500, message = "进口关税备注不能超过 500 字")
    private String tariffNote;
    private List<Integer> certifications;
}
