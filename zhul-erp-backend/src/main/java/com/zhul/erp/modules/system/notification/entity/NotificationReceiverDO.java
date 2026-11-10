package com.zhul.erp.modules.system.notification.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 通知接收人（按岗位） */
@Data
@TableName("notification_receiver")
public class NotificationReceiverDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private String postCode;
    /** 收货人岗位为 0 表示「采购订单的采购员」 */
    private Long userId;
    /** 0-已移除、1-有效 */
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
