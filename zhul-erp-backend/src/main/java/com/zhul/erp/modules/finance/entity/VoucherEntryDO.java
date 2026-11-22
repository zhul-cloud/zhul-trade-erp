package com.zhul.erp.modules.finance.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 业务凭证分录：记业务项，T+ 科目在导出时按科目设置取 */
@Data
@TableName("fin_voucher_entry")
public class VoucherEntryDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long voucherId;
    private Integer lineNo;
    private String itemKey;
    /** 0-无、1-供应商 */
    private Integer partnerType;
    private Long partnerId;
    /** 1-借、2-贷 */
    private Integer direction;
    private String currencyCode;
    private BigDecimal amount;
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
