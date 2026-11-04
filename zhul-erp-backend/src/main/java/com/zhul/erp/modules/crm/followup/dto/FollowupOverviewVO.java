package com.zhul.erp.modules.crm.followup.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 顶部链路卡片与阶段导航的计数 */
@Data
public class FollowupOverviewVO {
    private Integer todayOverdue;
    private Integer todayDue;
    private List<Board> boards = new ArrayList<>();

    @Data
    public static class Board {
        private Integer board;
        private String name;
        /** 第几步 */
        private Integer step;
        /** 本期只有商机成交上线，其余为 false（显示「即将上线」） */
        private Boolean available;
        /** 客户数（按客户去重，不含培育中） */
        private Integer customers;
        private Integer overdue;
        private Integer total;
        private Integer nurture;
        private List<Group> groups = new ArrayList<>();
    }

    @Data
    public static class Group {
        private Integer group;
        private String name;
        private Integer count;
        private List<Stage> stages = new ArrayList<>();
    }

    @Data
    public static class Stage {
        private String code;
        private String name;
        private Integer count;
    }
}
