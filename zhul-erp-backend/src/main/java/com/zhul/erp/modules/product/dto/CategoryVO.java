package com.zhul.erp.modules.product.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class CategoryVO {
    private Long id;
    private String categoryCode;
    private String categoryName;
    private String categoryNameZh;
    /** 上级品类ID，一级品类为空 */
    private Long parentId;
    /** 所属行业编码；细分品类为空、跟随上级 */
    private String industryCode;
    private String description;
    /** 中文简介 */
    private String descriptionZh;
    private Integer sortOrder;
    /** 自己填的默认海关编码，空表示未填 */
    private String hsCode;
    /** 实际使用的默认海关编码：自己没填时沿用上级品类的；都没填为空 */
    private String effectiveHsCode;
    /** 实际使用的编码是否沿用上级品类 */
    private Boolean hsInherited;
    /** 实际编码前 6 位的目的国关税参考：已填国家数、总数、未填的国家、税率范围 */
    private Integer tariffFilled;
    private Integer tariffTotal;
    private List<String> tariffMissing;
    private String tariffRange;
    private Integer status;
    /** 下面未删除的商品数量 */
    private Long productCount;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
    /** 细分品类（仅树形接口的一级品类返回） */
    private List<CategoryVO> children;
}
