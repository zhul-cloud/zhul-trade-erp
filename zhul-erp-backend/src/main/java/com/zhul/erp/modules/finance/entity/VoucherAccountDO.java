package com.zhul.erp.modules.finance.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 业务凭证科目设置：会计改过的业务项；没有记录的用默认科目（见 VoucherItems） */
@Data
@TableName("fin_voucher_account")
public class VoucherAccountDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private String itemKey;
    private String accountCode;
    private String accountName;
    /** 0-停用、1-启用 */
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT)
    private String createBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
}
