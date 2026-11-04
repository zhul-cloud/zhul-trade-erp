package com.zhul.erp.modules.crm.followup.repository;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;

/** 跟进对象的列表 / 待办查询条件（只列未结束、未删除的）；ownerIds 为 null 表示不限负责人 */
@Data
public class FollowupItemFilter {
    private Integer board;
    private Integer group;
    private String stage;
    /** true 只看培育中，false 不含培育中，null 都要 */
    private Boolean nurture;
    private Collection<Long> ownerIds;
    private Long customerId;
    /** 卡点编码 */
    private String blocker;
    /** 客户英文名 / 中文名、客户编码或单据编号包含 */
    private String keyword;
    private LocalDate dueFrom;
    private LocalDate dueTo;
    /** 算逾期天数用的今天 */
    private LocalDate today;
    /** 金额档阈值（本位币，从高到低） */
    private BigDecimal tier1;
    private BigDecimal tier2;
    private BigDecimal tier3;
    private Integer offset;
    private Integer size;
}
