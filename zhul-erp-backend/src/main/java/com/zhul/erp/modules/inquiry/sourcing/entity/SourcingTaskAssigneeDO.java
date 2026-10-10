package com.zhul.erp.modules.inquiry.sourcing.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 询价任务分配：一个任务可分给多人比价，改派时旧记录置为无效 */
@Data
@TableName("sourcing_task_assignee")
public class SourcingTaskAssigneeDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long taskId;
    private Long assigneeId;
    private Long assignedBy;
    private LocalDateTime assignedAt;
    private Integer assignMode;
    private Integer active;
    private LocalDateTime submittedAt;
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
