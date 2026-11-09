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
    /** 客户国家，精确匹配 */
    private String country;
    /** true 只看有履约异常的（订单履约末尾的「有异常」） */
    private Boolean exceptionOnly;
    /** 客户经营：只看沉睡 */
    private Boolean dormantOnly;
    /** true 不含下一步跟着售后单走的（A3、A4，今日待办不重复列出） */
    private Boolean excludeAfterSalesNext;
    private LocalDate dueFrom;
    private LocalDate dueTo;
    /** 算逾期天数用的今天 */
    private LocalDate today;
    /** 金额档阈值（本位币，从高到低） */
    private BigDecimal tier1;
    private BigDecimal tier2;
    private BigDecimal tier3;
    /** 只看这些客户（按客户分组的列表取页内客户的单据） */
    private Collection<Long> customerIds;
    /** 按客户分组：客户所处阶段（名下单据最靠后的阶段）的序号在这些里面，见 CustomerRollup.rank */
    private Collection<Integer> stageRanks;
    /** 按客户分组：名下有单据满足的条件（卡点、关键字、有异常、沉睡） */
    private String matchBlocker;
    private String matchKeyword;
    private Boolean matchException;
    private Boolean matchDormant;
    private Integer offset;
    private Integer size;
}
