package com.zhul.erp.modules.product.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 商品技术资料表 */
@Data
@TableName("product_document")
public class ProductDocumentDO {
    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 租户ID（0=平台级共享，本模块所有数据均为0） */
    private Integer tenantId;
    /** 商品ID，关联product.id；资料通过商品ID强绑定，只属于一个商品 */
    private Long productId;
    /** 文档类型（1-Datasheet、2-Manual、3-Installation Guide、4-User Manual、5-CAD、6-Drawing、7-Brochure、8-Certificate） */
    private Integer documentType;
    /** 文档标题 */
    private String title;
    /** 文件地址，仅允许http://、https://或以单个/开头的站内路径；本模块只存地址，不存文件本体 */
    private String fileUrl;
    /** 语言，如en/zh/ru */
    private String language;
    /** 文档版本 */
    private String version;
    /** 数据来源，如Manufacturer Website */
    private String source;
    /** 是否已核实（0-未核实、1-已核实）；仅为标记，不影响读取 */
    private Integer verified;
    /** 核实时间；verified=1时由服务端填写 */
    private LocalDateTime verifiedAt;
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
