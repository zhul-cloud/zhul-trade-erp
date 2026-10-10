package com.zhul.erp.modules.logistics.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 货代月结对账单 */
@Data
@TableName("forwarder_statement")
public class ForwarderStatementDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long forwarderId;
    /** YYYY-MM */
    private String period;
    /** 1-草稿、2-已确认 */
    private Integer status;
    private BigDecimal ourTotal;
    private BigDecimal statementTotal;
    private Long confirmedBy;
    private LocalDateTime confirmedAt;
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
