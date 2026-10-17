package com.zhul.erp.modules.listing.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 发品任务：本公司一个商品在一个平台上的发布记录 */
@Data
@TableName("listing_task")
public class ListingTaskDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long productId;
    /** 平台编码，见 MarketingPlatform */
    private String platform;
    /** 型号归并键，关联素材库 */
    private String assetKey;
    /** 1-待发布、2-已发布、3-已取消 */
    private Integer status;
    private String cancelReason;
    /** 参考素材的型号，为空表示用本型号素材 */
    private String refBrand;
    private String refModel;
    private String refBy;
    private LocalDateTime refTime;
    private String listingUrl;
    private String platformItemId;
    private String note;
    private String publishedBy;
    private LocalDateTime publishedAt;
    /** 发布或上次同步时该平台营销内容的写入时间 */
    private LocalDateTime contentAt;
    private String syncedBy;
    private LocalDateTime syncedAt;
    private String replacedBy;
    private LocalDateTime replacedAt;
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
