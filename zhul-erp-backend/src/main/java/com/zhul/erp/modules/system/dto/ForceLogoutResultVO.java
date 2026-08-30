package com.zhul.erp.modules.system.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ForceLogoutResultVO {
    private Integer successCount;
    private Integer failCount;
}
