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
