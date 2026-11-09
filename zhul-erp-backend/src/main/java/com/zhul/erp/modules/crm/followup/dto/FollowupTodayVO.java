package com.zhul.erp.modules.crm.followup.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 今日待办：按客户分「已逾期」「今天」「今天已跟进」三组，组内按客户价值排序；数字都是客户数。
 * 一个客户一张卡片，在多个板块都有到期单据时每个板块一行（boards）。
 */
@Data
public class FollowupTodayVO {
    private List<Customer> overdue = new ArrayList<>();
    private List<Customer> today = new ArrayList<>();
    /** 今天已经记过跟进、下一步仍是今天的客户，不计入待办 */
    private List<Customer> handled = new ArrayList<>();
    /** 本周还有（明天到周日有单据到期的客户） */
    private Integer weekRemaining;
    /** 明天要跟的客户数（空态提示用） */
    private Integer tomorrow;

    @Data
    public static class Customer {
        private Long customerId;
        private String customerName;
        private String country;
        private String bizGrade;
        private Integer potentialRating;
        private String ownerName;
        /** 名下到期单据中最大的逾期天数，0 为没有逾期 */
        private Integer overdueDays;
        private List<Board> boards = new ArrayList<>();
    }

    @Data
    public static class Board {
        private Integer board;
        /** 客户在这个板块所处的阶段（名下单据最靠后的阶段） */
        private String stageCode;
        private String stageName;
        /** 这个板块里最紧急的到期单据 */
        private FollowupItemVO lead;
        /** 这个板块里的到期单据（今天已跟进的组为今天已跟进的单据） */
        private List<FollowupItemVO> items = new ArrayList<>();
    }
}
