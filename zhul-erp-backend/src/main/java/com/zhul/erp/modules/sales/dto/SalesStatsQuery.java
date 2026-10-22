package com.zhul.erp.modules.sales.dto;

import lombok.Data;

import java.time.LocalDate;

/** 销售统计查询：日期范围（默认本年）、业务员、视图 */
@Data
public class SalesStatsQuery {
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private Long ownerId;
    /** MONTH 按月份（默认）、OWNER 按业务员、CUSTOMER 按客户 */
    private String view;
    /** 事业部；不传时能看合计的看合计，否则看第一个可看的事业部 */
    private Integer businessUnitId;
}
