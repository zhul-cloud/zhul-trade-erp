package com.zhul.erp.modules.website.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 站点品类对照：ERP 品类 → 站点品类代码 */
@Data
@TableName("website_category_map")
public class WebsiteCategoryMapDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long siteId;
    private Long categoryId;
    private String siteCategory;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT)
    private String createBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
}
