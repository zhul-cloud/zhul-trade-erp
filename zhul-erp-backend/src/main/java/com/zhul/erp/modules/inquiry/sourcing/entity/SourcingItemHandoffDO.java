package com.zhul.erp.modules.inquiry.sourcing.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 询价型号转出记录：按型号退回时每个交出的型号一条，用于按人员统计工作量 */
@Data
@TableName("sourcing_item_handoff")
public class SourcingItemHandoffDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long inquiryItemId;
    private Long taskId;
    private Long fromUserId;
    private Integer quoteRound;
    private Integer reason;
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
