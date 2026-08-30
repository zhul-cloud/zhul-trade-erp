package com.zhul.erp.modules.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhul.erp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_config")
public class SysConfigDO extends BaseEntity {
    private Integer tenantId;
    private String configKey;
    private String configName;
    private String configValue;
    private String configType;
    private Integer isBuiltin;
    private Integer isEncrypted;
    private String configGroup;
    private String remark;
    private LocalDateTime deletedAt;
}
