package com.zhul.erp.modules.crm.followup.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 主动开发作战卡与客户详情的开发状态（第四期 design D12）。
 * state：open-开发中、stopped-已停止可重新开发、eligible-可以加入、none-不能加入（reason 说明原因）。
 */
@Data
public class DevelopCardVO {
    private Long customerId;
    private String state;
    private String reason;
    private Long itemId;
    private String stageCode;
    private String stageName;
    private String prospectReason;
    private Integer prospectSource;
    private String prospectSourceName;
    private Integer potentialRating;
    private Integer touchCount;
    private LocalDate firstTouchDate;
    /** 序列天数，如 1,4,9,16；currentDay 为当前所在节点（D5），0 为未开始 */
    private List<Integer> sequence;
    private Integer currentDay;
    private String signalText;
    private String stopReason;
    private String stopReasonName;
    private String stopNote;
    /** D4 及以后可以转商机 */
    private Boolean canConvert;
    private Report report;
    private List<Contact> contacts;

    /** 最近一份背调：status 1-背调中、2-已完成、3-失败；priority 1-立即重点跟进、2-正常跟进、3-低优先级、4-不建议跟进 */
    @Data
    public static class Report {
        private Long id;
        private Integer status;
        private Integer priority;
        private Integer score;
        private String keyFinding;
        private String firstAction;
    }

    @Data
    public static class Contact {
        private String name;
        private String title;
        private String email;
        private String whatsapp;
        private String phone;
        private Boolean primary;
    }
}
