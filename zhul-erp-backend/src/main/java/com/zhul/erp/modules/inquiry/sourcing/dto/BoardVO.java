package com.zhul.erp.modules.inquiry.sourcing.dto;

import lombok.Data;

import java.util.List;

/** 分配工作台 */
@Data
public class BoardVO {
    private BoardStatsVO stats;
    /** 当前页签的任务总数 */
    private Long total;
    /** 当前页签按品牌的汇总，覆盖全部页 */
    private List<BrandGroupVO> brands;
    /** 当前页的任务 */
    private List<BoardTaskVO> tasks;
    private List<PurchaserVO> purchasers;
}
