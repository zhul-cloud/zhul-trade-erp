package com.zhul.erp.modules.product.candidate.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 候选通过建档 */
@Data
public class ApproveCandidateRequest {
    /** EXISTING-选已有品牌、ALIAS-把品牌原文设为已有品牌的别名、NEW-新建品牌 */
    private String brandMode;
    /** EXISTING / ALIAS 时必填 */
    private Long brandId;
    /** NEW 时必填 */
    @Size(max = 64, message = "品牌名称不能超过 64 字")
    private String brandName;
    @NotNull(message = "请选择品类")
    private Long categoryId;
    private Long seriesId;
    /** 新建系列名称（与 seriesId 二选一） */
    @Size(max = 64, message = "系列名称不能超过 64 字")
    private String newSeriesName;
    @Size(max = 128, message = "原始型号不能超过 128 字")
    private String mpnRaw;
    @Size(max = 128, message = "展示型号不能超过 128 字")
    private String mpnDisplay;
    @Size(max = 255, message = "商品名称不能超过 255 字")
    private String productName;
    @Size(max = 500, message = "简短描述不能超过 500 字")
    private String shortDescription;
    /** 生命周期（商品口径 1-6）；不传按来源询盘型号建议 */
    private Integer lifecycleStatus;
    @Size(max = 255, message = "生命周期依据不能超过 255 字")
    private String lifecycleSource;
}
