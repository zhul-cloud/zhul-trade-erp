package com.zhul.erp.modules.system.dto;

import lombok.Data;

@Data
public class UserStatsVO {
    private Integer total;
    private Integer enabled;
    private Integer disabled;
    private Integer newThisMonth;
}
