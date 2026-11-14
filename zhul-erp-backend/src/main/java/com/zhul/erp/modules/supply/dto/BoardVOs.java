package com.zhul.erp.modules.supply.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 供应链跟进页的返回结构 */
public final class BoardVOs {

    private BoardVOs() {
    }

    /** 顶部卡片与阶段计数 */
    @Data
    public static class Overview {
        private int todayModels;
        private int overdue;
        private int dueToday;
        private int weekRest;
        /** 需求情报：需求对象数（观察中 + 暂不覆盖不计）、待归类信号数、观察中、待复核、暂不覆盖 */
        private int intelTotal;
        private int unclassified;
        private int watching;
        private int review;
        private int paused;
        /** 供应覆盖：已进入矩阵数，各状态数（键 1–4），近 30 天无货且未解决数，待确认数，单一渠道数 */
        private int coverTotal;
        private Map<Integer, Integer> coverage;
        private int stockoutOpen;
        private int pending;
        private int single;
        private boolean canEdit;
        private boolean canTeam;
        private boolean canProject;
        private boolean canCoverage;
        private boolean canSeeSupplier;
        private boolean canDev;
        private int evidenceDays;
        /** 供应商开发板块 */
        private DevVOs.Overview dev;
    }

    /** 一组：品牌 · 品类 */
    @Data
    public static class Group {
        private String key;
        private Long brandId;
        private String brandName;
        private Long categoryId;
        private String categoryName;
        private int models;
        private int stockoutModels;
        private int stockoutTimes;
        private Map<Integer, Integer> coverage;
        private int overdue;
        private LocalDate earliestDue;
        private String urgentAction;
        private int ownerCount;
        private String ownerName;
        private Long projectId;
        private String projectName;
        private int projectSolved;
        private int projectTotal;
        private List<DemandVO> items;
    }

    @Data
    public static class GroupPage {
        private long totalGroups;
        private long totalModels;
        private List<Group> groups;
    }

    /** 今日待办卡片 */
    @Data
    public static class TodayCard {
        private String key;
        private String brandName;
        private String categoryName;
        private Long brandId;
        private Long categoryId;
        /** intel / cover */
        private String board;
        private String stage;
        private boolean overdue;
        private int overdueDays;
        private String source;
        private String sopHint;
        private List<DemandVO> items;
        private Long projectId;
    }

    @Data
    public static class Today {
        private int overdue;
        private int dueToday;
        private int weekRest;
        private List<TodayCard> overdueCards;
        private List<TodayCard> todayCards;
        /** 供应商开发的待办（候选下一步、开发任务超期、系统提醒），数字已计入上面的 overdue / dueToday */
        private List<DevVOs.TodayItem> devItems;
    }

    /** 作战卡 */
    @Data
    public static class Card {
        private String code;
        private String name;
        private String goal;
        private List<String> checklist;
        private List<String> quality;
        private String gate;
        private String autoHint;
    }

    @Data
    public static class RecordResult {
        private Long logId;
        private int changed;
        /** 没有有效证据、保持原状态的需求对象 */
        private List<Long> skipped;
        /** 重复提交，本次未处理 */
        private boolean duplicate;
    }

    /** 时间线一条：信号或处理记录 */
    @Data
    public static class TimelineItem {
        /** signal / log */
        private String kind;
        private java.time.LocalDateTime time;
        private SignalVO signal;
        private Long logId;
        private Boolean system;
        private String operatorName;
        private String content;
        private String nextAction;
        private LocalDate nextActionDate;
        private String statusChange;
    }
}
