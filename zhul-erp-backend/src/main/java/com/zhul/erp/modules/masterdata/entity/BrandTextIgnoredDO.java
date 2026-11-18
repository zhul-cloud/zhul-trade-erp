package com.zhul.erp.modules.masterdata.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 「不是品牌」的写法（平台级，tenant_id 恒为 0）：不再出现在待确认品牌里，单据保持原样 */
@Data
@TableName("brand_text_ignored")
public class BrandTextIgnoredDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    /** 比较键：去首尾空格、转小写 */
    private String textKey;
    private String text;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT)
    private String createBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
}
