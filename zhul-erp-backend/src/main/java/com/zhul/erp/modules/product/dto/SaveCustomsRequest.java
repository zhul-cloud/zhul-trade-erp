package com.zhul.erp.modules.product.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 海关信息整体覆盖保存。 */
@Data
public class SaveCustomsRequest {
    /** HS 编码，去掉点和空格后须为 6~10 位数字，只存数字；空表示未维护 */
    private String hsCode;
    private String customsNameCn;
    private String customsNameEn;
    /** 默认原产国，ISO 3166-1 alpha-2 两位大写国家码 */
    private String originCountry;
    private String declarationElements;
    private String supervisionConditions;
    /** 出口退税率（%），0~100，保留 2 位小数 */
    private BigDecimal exportRebateRate;
}
