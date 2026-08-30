package com.zhul.erp.modules.system.dto;

import lombok.Data;

@Data
public class ConfigGroupCountVO {
    private String group;
    private Long count;

    public ConfigGroupCountVO(String group, Long count) {
        this.group = group;
        this.count = count;
    }
}
