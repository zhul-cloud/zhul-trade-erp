package com.zhul.erp.modules.inquiry.sourcing.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 询价任务：按品牌 + 品类拆出的分配单元，只引用型号明细 */
@Data
@TableName("sourcing_task")
public class SourcingTaskDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private String taskCode;
    private Long customerInquiryId;
    private String brand;
    private String brandKey;
    private String category;
    private Integer itemCount;
    private Integer urgent;
    /** 见 InquiryConstants.TASK_* */
    private Integer status;
    private LocalDateTime firstAssignedAt;
    private LocalDateTime completedAt;
    private Integer returnReason;
    private String returnNote;
    private Long returnedBy;
    private LocalDateTime returnedAt;
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
