package com.zhul.erp.modules.system.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_log")
public class SysLogDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private String belongCode;
    private String belongName;
    /** 1-登录日志 2-操作日志 */
    private Integer type;
    private String operatorCode;
    private String operatorName;
    private LocalDateTime operateTime;
    private String operation;
    private String content;
    /** 操作结果（0-失败、1-成功） */
    private Integer result;
    private String menu;
    private String ip;
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
