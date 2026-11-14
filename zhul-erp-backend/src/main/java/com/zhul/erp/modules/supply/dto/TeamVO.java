package com.zhul.erp.modules.supply.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 供应链团队视图（spec supply-chain/followup「团队视图」） */
@Data
public class TeamVO {
    private int days;
    private int stockoutModels;
    private int stockoutTimes;
    private int prevStockoutModels;
    private int prevStockoutTimes;
    /** 比率为 null 时页面显示「—」 */
    private BigDecimal stockoutRate;
    private BigDecimal coverRate;
    private BigDecimal quotableRate;
    private BigDecimal backupRate;
    private int filledGaps;
    private List<Rank> byBrand;
    private List<Rank> byCategory;
    private List<Rank> cells;
    private List<Rank> reasons;
    private List<ProjectVO> projects;
    private List<OwnerRow> owners;
    private List<Attention> attention;

    @Data
    public static class Rank {
        private Long brandId;
        private Long categoryId;
        private String label;
        private int models;
        private int times;
        private int open;
        private Long projectId;
    }

    @Data
    public static class OwnerRow {
        private Long ownerId;
        private String ownerName;
        private int models;
        private int noChannel;
        private int lead;
        private int confirm;
        private int dueToday;
        private int overdue;
        private int multiDefer;
        private int records;
    }

    @Data
    public static class Attention {
        /** overdue / defer / no-owner / project */
        private String kind;
        private Long demandId;
        private Long projectId;
        private String title;
        private String detail;
        private String ownerName;
    }
}
