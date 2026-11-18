package com.zhul.erp.modules.product.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CustomsVO {
    private Long id;
    private Long productId;
    private String hsCode;
    private String customsNameCn;
    private String customsNameEn;
    private String originCountry;
    private String declarationElements;
    private String supervisionConditions;
    private BigDecimal exportRebateRate;
    /** 所属品类的默认海关编码（细分品类没填时沿用上级），都没填为空 */
    private String categoryHsCode;
    /** 默认编码所在品类的名称 */
    private String categoryHsFrom;
    /** 目的国进口关税参考：按商品的 HS 编码，没填时按品类默认编码；两者都没有为空 */
    private TariffVO tariff;
    /** 关税参考按哪个编码：product-商品的 HS 编码、category-品类默认编码 */
    private String tariffBy;
}
