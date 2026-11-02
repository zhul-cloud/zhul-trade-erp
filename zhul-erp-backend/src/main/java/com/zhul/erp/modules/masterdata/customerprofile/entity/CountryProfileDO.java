package com.zhul.erp.modules.masterdata.customerprofile.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 国家资料（各公司自己维护） */
@Data
@TableName("country_profile")
public class CountryProfileDO {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private Integer tenantId;
    private String country;
    /** JSON：字典 customer_voltage 码值数组 */
    private String voltages;
    private String plug;
    private String holidays;
    /** 外汇与支付管制（0-未填、1-无、2-一般、3-严格） */
    private Integer fxControl;
    private String fxNote;
    private String tariffNote;
    /** JSON：字典 customer_certification 码值数组 */
    private String certifications;
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
