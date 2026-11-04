package com.zhul.erp.modules.crm.followup.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 整理结果。status：1-排队中、2-处理中、3-完成、4-失败（与 ai_task 一致）；完成时 result 有值。
 * 前端 2 秒查一次，60 秒没有结果按失败处理。
 */
@Data
public class ExtractResultVO {
    private Long taskId;
    private Integer status;
    private String errorMessage;
    private Result result;

    @Data
    public static class Result {
        private String summary;
        /** 位掩码：1-客户给了反馈、2-客户在谈条件、4-没回复 */
        private Integer situation;
        private String blockerCode;
        private String blockerName;
        private List<NextAction> nextActions = new ArrayList<>();
        private List<ProfileSuggestion> profileSuggestions = new ArrayList<>();
        /** 订单履约、售后评价阶段的答案建议（只在对应阶段给出，前端只预填与当前阶段匹配的）：A1 客户收到了吗 */
        private Integer receiptAnswer;
        /** A2 用得好吗 */
        private Integer useAnswer;
        /** A1、A2 有问题时的问题类型 */
        private List<String> issueTypes = new ArrayList<>();
        /** A5 满意度 */
        private Integer satisfaction;
        /** A5、A6 评价状态 */
        private Integer reviewStatus;
        /** F6 新的预计送达、A1 还没收到时的新日期 */
        private LocalDate etaDate;
        /** 客户经营的客户情况建议（单选）：1-有新需求、2-聊了新品类、3-当前没有需求、4-没回复；其他板块为空 */
        private Integer accountAnswer;
        /** 主动开发的触达结果建议：2-客户回复了、3-出现采购信号、5-明确拒绝；其他板块为空 */
        private Integer touchAnswer;
        /** 出现采购信号时的型号、数量、时间 */
        private String signalText;
        /** 原文超过 20,000 字已截断 */
        private Boolean truncated;
    }

    @Data
    public static class NextAction {
        private String text;
        private LocalDate date;
    }

    @Data
    public static class ProfileSuggestion {
        private String field;
        private String label;
        private String value;
        private String evidence;
        /** 目标字段当前为空时默认勾选 */
        private Boolean checked;
    }
}
