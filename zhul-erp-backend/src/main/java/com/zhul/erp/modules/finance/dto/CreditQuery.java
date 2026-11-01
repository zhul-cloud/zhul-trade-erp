package com.zhul.erp.modules.finance.dto;

import lombok.Data;

/** 余额列表查询 */
@Data
public class CreditQuery {
    private Integer page = 1;
    private Integer pageSize = 20;
    /** 客户名称 */
    private String keyword;
    private String currencyCode;
    /** 事业部；0 表示没有事业部 */
    private Integer businessUnitId;
    private Long customerId;
    /** 显示余额为 0 的账户 */
    private Boolean showAll;
}
