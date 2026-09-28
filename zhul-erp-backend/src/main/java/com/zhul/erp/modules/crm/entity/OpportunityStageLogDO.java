package com.zhul.erp.modules.crm.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 商机阶段变更记录，只增不改 */
@Data
@TableName("opportunity_stage_log")
public class OpportunityStageLogDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long opportunityId;
    private String fromStage;
    private String toStage;
    /** 1-登记、2-阶段变更、3-标记结束、4-重新打开 */
    private Integer action;
    private Integer reason;
    private String note;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT)
    private String createBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
}
