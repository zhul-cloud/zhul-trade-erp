package com.zhul.erp.modules.product.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ProductVO {
    private Long id;
    private Long brandId;
    private String brandName;
    /** 品牌是否原厂正品；为 0 时下游不得使用"正品"类表述 */
    private Integer brandIsGenuine;
    private Long categoryId;
    private String categoryCode;
    private String categoryName;
    private Long seriesId;
    private String seriesName;
    private String mpnRaw;
    private String mpnNormalized;
    private String mpnDisplay;
    private String productName;
    private String shortDescription;
    private String specSummary;
    private Integer lifecycleStatus;
    private String lifecycleSource;
    private Integer status;
    /** 是否已软删除（仅平台账号带 includeDeleted 查询时可能为 true） */
    private Boolean deleted;
    /** 被业务单据引用的次数，仅详情返回 */
    private Long usageCount;
    /** 保存成功但需要提醒的事项，如"停产无替代"却存在替代关系 */
    private List<String> warnings;
    /** 档案完整度，仅平台账号返回，租户账号为 null */
    private CompletenessVO completeness;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
