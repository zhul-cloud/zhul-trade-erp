package com.zhul.erp.modules.product.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProductQuery extends PageQuery {
    /** 匹配型号（忽略空格、连字符、大小写）或产品名称 */
    private String keyword;
    private Long brandId;
    private Long categoryId;
    private Long seriesId;
    private Integer lifecycleStatus;
    private Integer status;
    /** 仅平台账号有效：同时返回已软删除的商品 */
    private Boolean includeDeleted;
    /** 仅平台账号有效：按缺项筛选，取值 media、logistics、customs、price；租户账号传入会被忽略 */
    private String missing;
}
