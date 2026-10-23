package com.zhul.erp.modules.product.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class CategoryQuery extends PageQuery {
    /** 按品类编码或名称模糊匹配 */
    private String keyword;
    private Integer status;
    /** 上级品类ID：为空时只查一级品类 */
    private Long parentId;
    /** 按行业筛选（平台账号用；租户账号固定为本租户行业） */
    private String industryCode;
}
