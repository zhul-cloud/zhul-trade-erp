package com.zhul.erp.modules.warehouse.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 新增 / 修改快递时效规则 */
@Data
public class SaveTransitTimeRequest {
    @NotBlank(message = "请填写快递公司")
    @Size(max = 32, message = "快递公司不能超过32个字")
    private String carrier;
    /** 留空表示该快递公司的默认天数 */
    @Size(max = 16, message = "发货省份不能超过16个字")
    private String originProvince;
    @NotNull(message = "请填写运输天数")
    @Min(value = 1, message = "运输天数为 1 – 30 天")
    @Max(value = 30, message = "运输天数为 1 – 30 天")
    private Integer days;
    @Size(max = 100, message = "备注不能超过100个字")
    private String remark;
}
