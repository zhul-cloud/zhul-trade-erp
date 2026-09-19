package com.zhul.erp.modules.product.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 品牌主数据表 */
@Data
@TableName("product_brand")
public class ProductBrandDO {
    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 租户ID（0=平台级共享，本模块所有数据均为0） */
    private Integer tenantId;
    /** 品牌名称，如Siemens/ABB */
    private String brandName;
    /** 原产国/地区 */
    private String country;
    /** Logo图片地址 */
    private String logoUrl;
    /** 品牌主题色（HEX），独立站展示用 */
    private String brandColor;
    /** 是否原厂正品品牌（0-兼容/非原厂、1-原厂正品）；为0时下游不得对该品牌商品使用"Genuine"类正品断言 */
    private Integer isGenuine;
    /** 状态（0-禁用、1-启用） */
    private Integer status;
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
