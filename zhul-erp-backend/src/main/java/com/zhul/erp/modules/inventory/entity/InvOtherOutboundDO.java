package com.zhul.erp.modules.inventory.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 其他出库单 */
@Data
@TableName("inv_other_outbound")
public class InvOtherOutboundDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private String outboundNo;
    /** 1-报废、2-转样品、3-货代丢失、4-货损 */
    private Integer outboundType;
    private Long warehouseId;
    private LocalDate outboundDate;
    private String reason;
    /** 1-已出库、2-已作废 */
    private Integer status;
    private String currencyCode;
    private BigDecimal amountCny;
    private String sourceDocType;
    private Long sourceDocId;
    private String sourceDocNo;
    private Long sourceRefId;
    private String remark;
    private String voidReason;
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
