package com.zhul.erp.modules.crm.followup.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** SOP 卡点字典（平台 tenant 0，租户可覆盖） */
@Data
@TableName("sop_blocker")
public class SopBlockerDO {

    @TableId(type = IdType.AUTO)
    private Integer id;
    private Integer tenantId;
    private Integer board;
    private String code;
    private String name;
    /** 对应的商机输单原因码，0 为不对应 */
    private Integer lostReason;
    /** JSON 字符串数组 */
    private String playbook;
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
