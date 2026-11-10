package com.zhul.erp.modules.system.notification.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 站内通知 */
@Data
@TableName("notification")
public class NotificationDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long recipientId;
    private String nodeCode;
    /** 1-待办、2-知会 */
    private Integer kind;
    private String mergeKey;
    private String bizNo;
    private String link;
    /** JSON 数组 */
    private String bizIds;
    private Integer itemCount;
    private String content;
    private Integer urgent;
    private LocalDateTime readAt;
    private LocalDateTime doneAt;
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
