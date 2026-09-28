package com.zhul.erp.modules.crm.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 商机阶段配置（平台级 tenant_id=0）；第二期在阶段上挂 SOP 清单 */
@Data
@TableName("opportunity_stage")
public class OpportunityStageDO {

    @TableId(type = IdType.AUTO)
    private Integer id;
    private Integer tenantId;
    private String code;
    private String name;
    /** 1-进行中、2-赢单、3-输单、4-无效 */
    private Integer category;
    /** 进入该阶段即计为有效（0/1） */
    private Integer countsAsValid;
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
