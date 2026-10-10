package com.zhul.erp.modules.product.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class BrandQuery extends PageQuery {
    /** 按品牌名称模糊匹配 */
    private String keyword;
    private Integer status;
    /** 品牌等级（0-普通、1-常做、2-核心），不传为全部 */
    private Integer brandLevel;
    /** 原产地（国家清单英文名），不传为全部 */
    private String country;
}
