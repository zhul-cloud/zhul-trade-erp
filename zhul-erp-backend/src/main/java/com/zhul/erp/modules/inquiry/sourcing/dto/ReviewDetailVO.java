package com.zhul.erp.modules.inquiry.sourcing.dto;

import lombok.Data;

import java.util.List;

/** 待审核任务详情 */
@Data
public class ReviewDetailVO {
    private BoardTaskVO task;
    private List<ReviewItemVO> items;
    /** 任务中还没有任何回价（也不在待审核）的型号数 */
    private Integer unsubmittedCount;
}
