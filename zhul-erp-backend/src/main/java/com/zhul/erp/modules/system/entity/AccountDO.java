package com.zhul.erp.modules.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhul.erp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
/**
 * 账号表实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("account")
public class AccountDO extends BaseEntity {

    /** 租户ID */
    private Integer tenantId;

    /** 用户ID */
    private Integer userId;

    /** 账号名 */
    private String username;

    /** 手机号 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 是否管理员（0-否、1-是） */
    private Integer adminFlag;

    /** 最后登录时间（varchar，格式：yyyy-MM-dd HH:mm:ss） */
    private String lastLoginTime;

    /** 最后登出时间（varchar，格式：yyyy-MM-dd HH:mm:ss） */
    private String lastLogoutTime;

    /** 登录状态（0-未登录、1-已登录） */
    private Integer loginStatus;

    /** 状态（0-禁用、1-启用） */
    private Integer status;
}
