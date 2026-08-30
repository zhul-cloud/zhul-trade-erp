package com.zhul.erp.modules.system.entity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhul.erp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("account_local_auth")
public class AccountLocalAuthDO extends BaseEntity {
    private Integer accountId;
    private String username;
    private String password;
    private String salt;
    private Integer failCount;
    private LocalDateTime lastFailAt;
    private LocalDateTime unlockAt;
    private String resetToken;
    private LocalDateTime resetTokenExpiresAt;
    private Integer resetFailCount;
}
