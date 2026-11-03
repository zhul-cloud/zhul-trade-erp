package com.zhul.erp.modules.masterdata.customerbackground.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;

/** 背调报告里的一条字段建议，连同采纳情况存在报告的 suggestions 列 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
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

    // ---- 以下只在报告详情里返回，不存库（保存前为空，按 NON_NULL 不写入）
    /** 字段中文名 */
    private String label;
    /** 档案里的当前值（联系人字段为匹配到的联系人的值），空为没有值 */
    private String currentValue;
    /** 不能采纳的原因，如「无法采纳：选项不存在」 */
    private String unavailableReason;
    /** 待处理、能采纳且当前值为空的默认勾选 */
    private Boolean defaultChecked;
    private String operatorName;

    @Data
    public static class Contact {
        private String name;
        private String email;
        private String title;
    }
}
