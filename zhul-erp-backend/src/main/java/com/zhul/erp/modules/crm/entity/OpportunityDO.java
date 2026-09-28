package com.zhul.erp.modules.crm.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 商机：一个新客户从首次接触到赢单 / 输单 / 无效的获客过程；一个客户最多一条 */
@Data
@TableName("opportunity")
public class OpportunityDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private String opportunityCode;
    private Long customerId;
    /** 来源渠道，同客户来源渠道枚举 */
    private Integer sourceChannel;
    /** 首次接触日期：统计归属日 */
    private LocalDate firstContactDate;
    private Long ownerId;
    private String stageCode;
    private LocalDateTime stageChangedAt;
    /** 进入结束状态前的阶段，重新打开时回到这里 */
    private String reopenStageCode;
    private Integer closeReason;
    private String closeNote;
    @TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private LocalDateTime closedAt;
    /** 是否曾进入有效阶段（0/1），只会从 0 变 1 */
    private Integer reachedValid;
    private String demandSummary;
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
