package com.zhul.erp.modules.product.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 目的国进口关税参考（平台级，tenant_id 恒为 0）：按海关编码前 6 位与国家 */
@Data
@TableName("hs_tariff_ref")
public class HsTariffRefDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    /** 海关编码前 6 位 */
    private String hs6;
    /** 目的国两位国家码 */
    private String countryCode;
    /** 进口关税文字，空表示未填 */
    private String rate;
    private String note;
    /** 来源（AI 补全时的网址或说明） */
    private String source;
    private LocalDate checkedAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT)
    private String createBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
}
