package com.zhul.erp.modules.supply.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 供应商开发（第二期）的返回结构；没有「查看货源信息」时供应商名称与联系方式为空 */
public final class DevVOs {

    private DevVOs() {
    }

    /** 板块卡片与阶段计数 */
    @Data
    public static class Overview {
        /** 有缺口的范围数 = 待开发 + 开发中 */
        private int gapGroups;
        private int todo;
        private int doing;
        private int done;
        /** 所有范围缺口家数之和 */
        private int gapTotal;
    }

    /** 合格或线索供应商 */
    @Data
    public static class Supplier {
        private Long id;
        private String name;
        /** 合格依据：1 询价记录、2 采购订单行；线索为 0 */
        private int evidenceType;
        private LocalDate evidenceDate;
    }

    /** 候选供应商 */
    @Data
    public static class Candidate {
        private Long id;
        private Long projectId;
        private Long supplierId;
        private String name;
        private String contactName;
        private String contactInfo;
        private Integer source;
        private Integer stage;
        private String abandonReason;
        private String note;
        private Integer evidenceType;
        private Long evidenceId;
        private LocalDate evidenceDate;
        private LocalDate quotedAt;
        private String nextAction;
        private LocalDate nextActionDate;
        private Long ownerId;
        private String ownerName;
        private String lastAction;
        private LocalDateTime lastActionAt;
        private String lastActionByName;
        /** 下一步逾期天数，没有逾期为 0 */
        private int overdueDays;
        /** 已报价超过 7 天还没关联供应商库 */
        private boolean linkReminder;
    }

    /** 一组：品牌 · 二级品类 */
    @Data
    public static class Group {
        private String key;
        private Long brandId;
        private String brandName;
        private Long categoryId;
        private String categoryName;
        private int heat;
        /** demand / expansion / both */
        private String source;
        private Long expansionId;
        private Integer expansionHeat;
        private String expansionReason;
        private int required;
        private int qualified;
        private int gap;
        /** todo / doing / done */
        private String stage;
        private int leads;
        private int stockoutModels;
        private int stockoutTimes;
        /** 进行中的专项（不论是否带开发目标） */
        private Long projectId;
        private String projectName;
        private Integer projectTarget;
        private int projectQualified;
        private Long projectOwnerId;
        private String projectOwnerName;
        private LocalDate projectDueDate;
        private int projectOverdueDays;
        /** 进行中（未放弃、未合格）的候选数 */
        private int activeCandidates;
        private String urgentAction;
        private LocalDate urgentDate;
        private int overdue;
        private List<Supplier> qualifiedSuppliers;
        private List<Supplier> leadSuppliers;
        private List<Candidate> candidates;
    }

    @Data
    public static class GroupPage {
        private long total;
        private List<Group> groups;
    }

    /** 建开发任务、加入主动拓展弹窗用的范围信息 */
    @Data
    public static class Scope {
        private Long brandId;
        private String brandName;
        private Long categoryId;
        private String categoryName;
        private int heat;
        private int required;
        private int qualified;
        private int gap;
        private Long expansionId;
        private Integer expansionHeat;
        private String expansionReason;
        private Long expansionOwnerId;
        private Long activeProjectId;
        private String activeProjectName;
        private Integer activeProjectTarget;
        private String defaultName;
    }

    /** 专项详情的开发供应商区 */
    @Data
    public static class ProjectDev {
        private int target;
        /** 本专项下合格的候选数 */
        private int qualified;
        /** 范围内全部合格供应商数 */
        private int rangeQualified;
        private int required;
        private int rangeGap;
        private boolean suggestClose;
        private List<Candidate> candidates;
        /** 范围内其他合格供应商（不是本专项的候选） */
        private List<Supplier> others;
        private List<BoardVOs.TimelineItem> records;
        private boolean canEdit;
        private boolean canSeeSupplier;
    }

    /** 今日待办里的一条 */
    @Data
    public static class TodayItem {
        /** candidate：候选下一步；project：开发任务超期；remind：系统提醒 */
        private String kind;
        private String key;
        private Long projectId;
        private String projectName;
        private Long brandId;
        private String brandName;
        private Long categoryId;
        private String categoryName;
        private Long candidateId;
        private String candidateName;
        private String text;
        private LocalDate date;
        private boolean overdue;
        private int overdueDays;
    }

    @Data
    public static class Today {
        private int overdue;
        private int dueToday;
        private List<TodayItem> items;
    }

    @Data
    public static class RecordResult {
        private Long logId;
        private int changed;
        /** 已合格、阶段保持不变的候选 */
        private List<Long> skipped;
        private boolean duplicate;
    }
}
