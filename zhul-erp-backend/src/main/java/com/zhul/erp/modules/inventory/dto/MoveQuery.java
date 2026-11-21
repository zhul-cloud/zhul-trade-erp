package com.zhul.erp.modules.inventory.dto;

import lombok.Data;

import java.time.LocalDate;

/** 库存流水筛选；默认不含预占与释放 */
@Data
public class MoveQuery {
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private Long warehouseId;
    private Long productId;
    private Integer opType;
    private String bizType;
    /** 型号、单号、批次号 */
    private String keyword;
    private Boolean includeReservation;
    private Integer page;
    private Integer pageSize;
}
