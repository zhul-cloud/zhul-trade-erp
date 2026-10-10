package com.zhul.erp.modules.logistics.dto;

import lombok.Data;

import java.time.LocalDate;

/** 出运单列表的查询 */
@Data
public class LogisticsPageQuery {
    private Integer page;
    private Integer pageSize;
    /** 出运单号、订单号、运单号、客户 */
    private String keyword;
    private Long forwarderId;
    private Integer status;
    private LocalDate shippedFrom;
    private LocalDate shippedTo;
}
