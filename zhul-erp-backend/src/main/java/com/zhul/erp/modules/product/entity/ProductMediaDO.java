package com.zhul.erp.modules.product.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 商品图片与视频表 */
@Data
@TableName("product_media")
public class ProductMediaDO {
    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 租户ID（0=平台级共享，本模块所有数据均为0） */
    private Integer tenantId;
    /** 商品ID，关联product.id */
    private Long productId;
    /** 媒体类型（1-图片、2-视频） */
    private Integer mediaType;
    /** 文件地址：上传后为站内路径（/uploads/product/...），外链为http(s)地址；不允许其他协议 */
    private String fileUrl;
    /** 存储方式（1-平台上传、2-外部链接） */
    private Integer storageType;
    /** 视频封面地址，仅视频使用，地址规则同file_url */
    private String coverUrl;
    /** 标题；图片时同时作为替代文字（无障碍与SEO用） */
    private String title;
    /** 文件大小（字节），仅上传时记录，外链为0 */
    private Long fileSize;
    /** 是否主图（0-否、1-是）；仅图片可为1，同一商品未删除行内最多一张 */
    private Integer isMain;
    /** 来源，如 Manufacturer Website */
    private String source;
    /** 排序 */
    private Integer sortOrder;
    /** 软删除时间，NULL表示未删除 */
    private LocalDateTime deletedAt;
    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    /** 创建人 */
    @TableField(fill = FieldFill.INSERT)
    private String createBy;
    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    /** 更新人 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
}
