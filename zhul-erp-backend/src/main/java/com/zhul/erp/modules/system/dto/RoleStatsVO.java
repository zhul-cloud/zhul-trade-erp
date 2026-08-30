package com.zhul.erp.modules.system.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class RoleStatsVO {
    private Long total;
    private Long enabledCount;
    private Long builtInCount;
    private Long customCount;
    private Long deptCoverage;
    private BigDecimal enabledRate;
}
