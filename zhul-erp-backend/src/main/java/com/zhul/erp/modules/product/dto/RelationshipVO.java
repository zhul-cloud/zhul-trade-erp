package com.zhul.erp.modules.product.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RelationshipVO {
    private Long id;
    private Long productId;
    private String relatedMpn;
    private Long relatedProductId;
    /** 关联商品在目录内时的展示型号与品牌，便于列表直接显示 */
    private String relatedProductDisplay;
    private String relatedBrandName;
    private Integer relationshipType;
    private Integer confidence;
    private String note;
    private String verifiedBy;
    private LocalDateTime verifiedAt;
    private Integer sortOrder;
}
