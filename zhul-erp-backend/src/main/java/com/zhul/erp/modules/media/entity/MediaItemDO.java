package com.zhul.erp.modules.media.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 素材索引：一条素材 × 一个型号一行，指向来源文件记录；文件信息不冗余，展示时按来源取 */
@Data
@TableName("media_item")
public class MediaItemDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private String assetKey;
    private String brand;
    private String model;
    /** 类别，见 MediaCategory */
    private Integer category;
    /** 1-图片、2-视频、3-文件 */
    private Integer kind;
    /** biz_attachment、customer_inquiry_attachment、product_media、product_document */
    private String sourceTable;
    private Long sourceId;
    /** 0-仅内部、1-可发客户 */
    private Integer canSend;
    /** 0-否、1-用于发品 */
    private Integer forListing;
    private String note;
    private Long flagBy;
    private LocalDateTime flagTime;
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
