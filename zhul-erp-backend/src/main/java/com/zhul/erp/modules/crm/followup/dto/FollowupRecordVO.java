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
    /** 快速记录 */
    private Boolean quick;
    /** 实际沟通日期，快速记录时有 */
    private LocalDate contactDate;
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
    /** 这次跟进涉及的单据编号（涉及多张时前端显示「涉及 n 张单据」，点开列出）；只涉及一张或没有时为空 */
    private List<String> docNos = new ArrayList<>();
    private LocalDateTime createTime;

    @Data
    public static class ProfileField {
        private String field;
        private String label;
        private String value;
    }
}
