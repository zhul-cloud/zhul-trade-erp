package com.zhul.erp.modules.crm.followup.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** SOP 阶段作战卡（平台 tenant 0，租户可覆盖） */
@Data
@TableName("sop_stage")
public class SopStageDO {

    @TableId(type = IdType.AUTO)
    private Integer id;
    private Integer tenantId;
    private Integer board;
    private String stageCode;
    private String name;
    private String goal;
    /** JSON 字符串数组 */
    private String checklist;
    private String gateText;
    private String autoHint;
    /** JSON 对象：提醒节点 → 默认动作 */
    private String defaultActions;
    private String exampleDialog;
    private Integer refDays;
    private Integer sortOrder;
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT)
    private String createBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
}
