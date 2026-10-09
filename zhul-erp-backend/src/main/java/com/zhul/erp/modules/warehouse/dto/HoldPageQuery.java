package com.zhul.erp.modules.warehouse.dto;

import lombok.Data;

/** 暂存货列表的查询 */
@Data
public class HoldPageQuery {
    private Integer page;
    private Integer pageSize;
    /** 型号、来源采购单号 */
    private String keyword;
    private Integer status;
}
