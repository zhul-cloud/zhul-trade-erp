package com.zhul.erp.modules.product.dto;

import lombok.Data;

/** 国家/地区清单项：品牌原产地下拉的数据源 */
@Data
public class CountryVO {
    /** ISO 3166-1 两位代码，如 DE */
    private String code;
    /** 英文名，品牌记录保存的就是它，如 Germany、Taiwan, China */
    private String nameEn;
    /** 中文名，如 德国 */
    private String nameZh;
}
