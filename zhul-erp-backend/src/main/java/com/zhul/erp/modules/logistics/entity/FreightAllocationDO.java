package com.zhul.erp.modules.logistics.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 运费分摊结果 */
@Data
@TableName("freight_allocation")
public class FreightAllocationDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    /** 1-国内快递、2-国际运费 */
    private Integer sourceType;
    private Long sourceId;
    private Long outboundId;
    private Long boxId;
    private Long soItemId;
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
