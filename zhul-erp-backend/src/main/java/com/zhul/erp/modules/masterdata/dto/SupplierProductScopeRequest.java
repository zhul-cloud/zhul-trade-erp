package com.zhul.erp.modules.masterdata.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 一个主营品牌及其细分品类。brandId 为正式品牌；没有 brandId 时按 brandName 归一（名称或别名匹配上的关联正式品牌，
 * 匹配不上的保存为待确认品牌）。categoryIds 为空表示该品牌全部品类，只能是细分品类。
 */
@Data
public class SupplierProductScopeRequest {
    private Long brandId;
    @Size(max = 64, message = "品牌名称不能超过64个字符")
    private String brandName;
    @Size(max = 60, message = "每个品牌最多选择60个细分品类")
    private List<Long> categoryIds;
}
