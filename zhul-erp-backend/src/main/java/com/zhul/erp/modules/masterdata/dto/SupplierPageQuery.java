package com.zhul.erp.modules.masterdata.dto;

import lombok.Data;

@Data
public class SupplierPageQuery {
    private Integer page = 1;
    private Integer pageSize = 10;
    /** 名称关键词（旧参数，保留兼容） */
    private String keyword;
    private String country;
    private Integer status;
    /** 编码，模糊匹配 */
    private String supplierCode;
    /** 名称，模糊匹配 */
    private String name;
    /** 统一社会信用代码，精确匹配（忽略大小写） */
    private String creditCode;
    private Integer supplierType;
    /** 主营品牌（正式品牌 ID） */
    private Long brandId;
    /** 主营细分品类 ID */
    private Long categoryId;
}
