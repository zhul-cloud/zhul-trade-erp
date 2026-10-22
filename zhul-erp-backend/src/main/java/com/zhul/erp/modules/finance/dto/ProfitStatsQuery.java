package com.zhul.erp.modules.finance.dto;

import lombok.Data;

import java.time.LocalDate;

/** 利润统计查询：销售日期范围（默认本年）、业务员、客户、新 / 老客户、只看已结算、分组方式 */
@Data
public class ProfitStatsQuery {
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private Long ownerId;
    private Long customerId;
    /** 1-新客户、2-老客户（同询盘的新老客户标记） */
    private Integer customerType;
    private Boolean settledOnly;
    /** 事业部；不传时能看合计的看合计，否则看第一个可看的事业部 */
    private Integer businessUnitId;
    /** MONTH 月份（默认）、OWNER 业务员、CUSTOMER 客户 */
    private String groupBy;
}
