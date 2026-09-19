package com.zhul.erp.modules.product.dto;

import lombok.Data;

@Data
public class SaveProductRequest {
    private Long brandId;
    private Long categoryId;
    /** 可选；必须属于 brandId 对应的品牌 */
    private Long seriesId;
    /** 原始型号，仅去首尾空格，不做其他清洗；商品被引用后不可修改 */
    private String mpnRaw;
    /** 展示型号，缺省等于原始型号 */
    private String mpnDisplay;
    private String productName;
    private String shortDescription;
    private String specSummary;
    /** 生命周期（1-在产、2-现行、3-旧款、4-已停产、5-停产无替代、6-未知），缺省为 6 */
    private Integer lifecycleStatus;
    /** 生命周期依据，4/5 时必填 */
    private String lifecycleSource;
}
