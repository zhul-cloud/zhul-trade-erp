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
    /** 页签数字：按搜索与采购员筛选后统计（顶部 stats 不受筛选影响） */
    private TabCounts tabs;

    @Data
    public static class TabCounts {
        private Integer unassigned;
        private Integer sourcing;
        private Integer review;
        private Integer done;
    }
}
