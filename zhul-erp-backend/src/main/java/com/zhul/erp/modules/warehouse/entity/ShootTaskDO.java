package com.zhul.erp.modules.warehouse.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 拍摄任务 */
@Data
@TableName("shoot_task")
public class ShootTaskDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long receiptId;
    private Long receiptItemId;
    private Long soId;
    private String model;
    private String brand;
    private String category;
    private String assetKey;
    /** 1-待拍摄、2-已完成、3-已跳过 */
    private Integer status;
    private String skipReason;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long reusedFromTaskId;
    private Long shooterId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime completedAt;
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
