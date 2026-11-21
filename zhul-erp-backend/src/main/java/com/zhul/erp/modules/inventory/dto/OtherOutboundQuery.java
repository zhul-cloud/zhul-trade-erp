package com.zhul.erp.modules.inventory.dto;

import lombok.Data;

/** 其他出库页查询：关键词（单号、型号）、出库类型、仓库、状态 */
@Data
public class OtherOutboundQuery {
    private String keyword;
    private Integer outboundType;
    private Long warehouseId;
    private Integer status;
    private Integer page;
    private Integer pageSize;
}
