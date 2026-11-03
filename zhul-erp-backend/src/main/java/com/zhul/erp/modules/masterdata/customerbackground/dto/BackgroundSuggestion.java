package com.zhul.erp.modules.masterdata.customerbackground.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 背调报告里的一条字段建议，连同采纳情况存在报告的 suggestions 列 */
@Data
public class BackgroundSuggestion {
    /** 待处理、已采纳、已忽略 */
    public static final String PENDING = "pending";
    public static final String ADOPTED = "adopted";
    public static final String IGNORED = "ignored";

    /** 所属模块 1–5 */
    private Integer module;
    /** 画像字段键 */
    private String field;
    private String value;
    private String evidence;
    private String sourceUrl;
    /** 联系人字段的定位信息 */
    private Contact contact;
    private String state;
    private Long operatorId;
    private LocalDateTime at;

    @Data
    public static class Contact {
        private String name;
        private String email;
        private String title;
    }
}
