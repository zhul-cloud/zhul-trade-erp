package com.zhul.erp.modules.aitask.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通用 AI 任务：Java 后端与外部 AI 编排服务之间的异步任务契约。
 * 与具体业务（询盘解析等）无关，未来新增 skill 时复用同一张表，见
 * openspec/changes/add-inquiry-management/design.md 决策3。
 */
@Data
@TableName("ai_task")
public class AiTaskDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private String skillId;
    /** 状态（1-排队中、2-处理中、3-已完成、4-失败） */
    private Integer status;
    /** 处理结果（AI服务回传的结构化JSON，原样存取，不在此层解析业务字段） */
    private String output;
    private String errorMessage;
    private Long requestedBy;
    private LocalDateTime startedAt;
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
