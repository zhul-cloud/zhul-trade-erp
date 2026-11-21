package com.zhul.erp.modules.inventory.dto;

import lombok.Data;

/** 成本调整页查询：所属月份（YYYY-MM）、触发原因、关键词（调整单号、来源单号、型号、批次号） */
@Data
public class CostAdjustQuery {
    private String month;
    private Integer reason;
    private String keyword;
    /** 只看有销售成本调整的 */
    private Boolean cogsOnly;
    private Integer page;
    private Integer pageSize;
}
