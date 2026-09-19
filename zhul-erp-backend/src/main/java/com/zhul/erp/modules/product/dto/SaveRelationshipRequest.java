package com.zhul.erp.modules.product.dto;

import lombok.Data;

@Data
public class SaveRelationshipRequest {
    /** 关联型号原文，可以是目录里没有的旧型号；从目录选择时可留空，由 relatedProductId 带出 */
    private String relatedMpn;
    /** 从目录中明确选择的关联商品；不传时由系统按型号自动匹配（仅在唯一命中时关联） */
    private Long relatedProductId;
    /** 1-官方直接替代、2-厂商后续型号、3-功能性替代、4-兼容、5-交叉引用、6-同系列 */
    private Integer relationshipType;
    /** 1-已验证、2-高、3-中、4-低、5-未知，缺省 3 */
    private Integer confidence;
    private String note;
    /** 核实人，置信度为"已验证"时必填 */
    private String verifiedBy;
    private Integer sortOrder;
    /** 仅创建时有效：对称类型（3/4/5/6）同时创建反向关系 */
    private Boolean createReverse;
}
