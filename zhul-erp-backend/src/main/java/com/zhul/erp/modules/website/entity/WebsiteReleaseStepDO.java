package com.zhul.erp.modules.website.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 发布单的一步执行记录 */
@Data
@TableName("website_release_step")
public class WebsiteReleaseStepDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long releaseId;
    private String step;
    private Integer attempt;
    /** 1-执行中、2-成功、3-失败、4-警告 */
    private Integer status;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private String message;
    /** output 是 JSqlParser 保留字，需加反引号 */
    @TableField("`output`")
    private String output;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT)
    private String createBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
}
