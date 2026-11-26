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
    /** 品类中文名（系统内展示用） */
    private String categoryNameZh;
    private Long seriesId;
    private String seriesName;
    private String mpnRaw;
    private String mpnNormalized;
    private String mpnDisplay;
    /** 订货号（物料号、料号），辅助识别 */
    private List<String> orderCodes;
    private String productName;
    private String shortDescription;
    private String specSummary;
    private Integer lifecycleStatus;
    private String lifecycleSource;
    private Integer status;
    /** 所属租户（0-平台） */
    private Integer ownerTenantId;
    /** 当前账号能否修改该商品（平台账号、或商品属于本租户；不含按钮权限） */
    private Boolean editable;
    /** 是否已软删除（仅平台账号带 includeDeleted 查询时可能为 true） */
    private Boolean deleted;
    /** 被业务单据引用的次数，仅详情返回 */
    private Long usageCount;
    /** 保存成功但需要提醒的事项，如"停产无替代"却存在替代关系 */
    private List<String> warnings;
    /** 档案完整度，仅平台账号返回，租户账号为 null */
    private CompletenessVO completeness;
    /** 列表：主图地址，没有主图为空 */
    private String mainImageUrl;
    private LocalDateTime createTime;
    private String createBy;
    private LocalDateTime updateTime;
    private String updateBy;
    /** 需求热度：关联的询盘型号数、有效销售订单数；租户只统计本公司 */
    private Long inquiryCount;
    private Long dealCount;
    /** AI 细分品类状态（0-无、1-待确认建议、2-已忽略） */
    private Integer aiCategoryState;
    /** AI 建议的细分品类（待确认建议时有值） */
    private Long aiCategoryId;
    private String aiCategoryName;
    /** 建议的细分品类不属于当前一级品类（当前为「其他」时不算） */
    private Boolean aiCategoryCross;
    /** 建议时间；已忽略时为忽略时间 */
    private LocalDateTime aiCategoryAt;
}
