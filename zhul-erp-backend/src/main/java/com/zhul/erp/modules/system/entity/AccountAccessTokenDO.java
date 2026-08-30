package com.zhul.erp.modules.system.entity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhul.erp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("account_access_token")
public class AccountAccessTokenDO extends BaseEntity {
    private Integer userId;
    private Integer accountId;
    private String accessToken;
    private String refreshToken;
    private String deviceInfo;
    private String loginIp;
    private Integer status;
    private LocalDateTime expiryTime;
    private LocalDateTime deletedAt;
}
