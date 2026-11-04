package com.zhul.erp.modules.crm.followup.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 客户跟进时间线的一条：业务员跟进或系统事件 */
@Data
public class FollowupRecordVO {
    private Long id;
    /** 1-业务员跟进、2-系统事件 */
    private Integer recordType;
    private String title;
    private Integer situation;
    private List<String> situationLabels = new ArrayList<>();
    private String blockerCode;
    private String blockerName;
    private String nextAction;
    private LocalDate nextActionDate;
    /** 这条跟进定的下一步是否已完成（之后又记过跟进） */
    private Boolean nextDone;
    private Integer channel;
    private String channelName;
    private String summary;
    private String rawContent;
    private String stageBefore;
    private String stageAfter;
    private List<ProfileField> profileFields = new ArrayList<>();
    private String operatorName;
    private Integer targetType;
    private String docNo;
    private LocalDateTime createTime;

    @Data
    public static class ProfileField {
        private String field;
        private String label;
        private String value;
    }
}
