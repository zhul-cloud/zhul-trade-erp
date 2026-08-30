package com.zhul.erp.modules.system.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConfigVO {
    private Integer id;
    private String configKey;
    private String configName;
    private String configValue;
    private String configType;
    private Integer isBuiltin;
    private Integer isEncrypted;
    private String configGroup;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
