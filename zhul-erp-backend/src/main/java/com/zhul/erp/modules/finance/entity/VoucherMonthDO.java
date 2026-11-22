package com.zhul.erp.modules.finance.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 业务凭证月份：生成与导出记录；反结账时软删除 */
@Data
@TableName("fin_voucher_month")
public class VoucherMonthDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long periodId;
    private String periodMonth;
    private Long generatedBy;
    private String generatedByName;
    private LocalDateTime generatedAt;
    private Integer exportCount;
    private Long exportedBy;
    private String exportedByName;
    private LocalDateTime exportedAt;
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
