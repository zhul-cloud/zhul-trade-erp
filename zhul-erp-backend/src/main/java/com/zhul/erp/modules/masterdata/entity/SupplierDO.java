package com.zhul.erp.modules.masterdata.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("supplier")
public class SupplierDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private String name;
    private String country;
    private String contactName;
    private String contactPhone;
    private String contactEmail;
    /** 主营品牌（逗号分隔，仅辅助展示，不做强校验） */
    private String mainBrands;
    /** 状态（0-禁用、1-启用） */
    private Integer status;
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
