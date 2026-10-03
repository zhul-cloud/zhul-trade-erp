package com.zhul.erp.modules.inquiry.item.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 客户询盘的型号明细：唯一的需求行，询价任务与询价记录都挂在它上面 */
@Data
@TableName("inquiry_item")
public class InquiryItemDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long customerInquiryId;
    private Integer lineNo;
    private String brand;
    private Long brandId;
    /** 品牌匹配键，见 PriceKeys */
    private String brandKey;
    private String category;
    private String originalModel;
    private String confirmedModel;
    /** 归一化型号，与商品主数据同规则 */
    private String modelKey;
    private Long productId;
    private Integer confidence;
    private String correctionNote;
    private Integer quantity;
    private String unit;
    private String description;
    private Integer lifecycle;
    private String replacementModel;
    private Integer difficulty;
    private String inquiryScript;
    /** JSON 数组 */
    private String searchKeywords;
    private Integer priceSource;
    private Integer quoteStatus;
    @TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private Long selectedQuoteId;
    /** 采购成本价是否由采购负责人手动指定 */
    private Integer costManual;
    @TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private Long sourcingTaskId;
    private String remark;
    private LocalDateTime deletedAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT)
    private String createBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
}
