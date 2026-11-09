package com.zhul.erp.modules.crm.followup.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按客户分组的一行（change followup-group-by-customer）：客户、所处阶段（名下单据最靠后的阶段）、单据数、
 * 最紧急的单据（lead，按客户价值排序后的第一张），以及这个客户在本板块的全部单据（items，同样排序）。
 */
@Data
public class CustomerGroupVO {
    private Long customerId;
    private String customerName;
    private String country;
    private String bizGrade;
    private Integer potentialRating;
    private String ownerName;
    private String stageCode;
    private String stageName;
    private Integer stageGroup;
    private Integer docCount;
    /** 各类单据张数：targetType → 张数（1-商机、2-询盘、3-报价单、4-PI、5-订单…） */
    private Map<Integer, Integer> typeCounts = new LinkedHashMap<>(8);
    /** 名下单据中最大的逾期天数，0 为没有逾期 */
    private Integer overdueDays;
    private FollowupItemVO lead;
    private List<FollowupItemVO> items = new ArrayList<>();
    /** 按阶段筛选时，在筛选范围内的单据；不在范围内的前端标灰。没有筛选时为 null */
    private List<Long> inScope;
}
