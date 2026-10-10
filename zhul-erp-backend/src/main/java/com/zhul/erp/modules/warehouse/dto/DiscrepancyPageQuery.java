package com.zhul.erp.modules.warehouse.dto;

import lombok.Data;

/** 到货差异列表的查询 */
@Data
public class DiscrepancyPageQuery {
    private Integer page;
    private Integer pageSize;
    /** 入库单号、采购单号、型号、采购对象 */
    private String keyword;
    private Integer type;
    private Integer status;
}
