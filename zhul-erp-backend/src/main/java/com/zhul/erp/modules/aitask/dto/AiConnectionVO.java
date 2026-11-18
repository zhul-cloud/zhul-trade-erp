package com.zhul.erp.modules.aitask.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 设置页的一张连接方式卡片：顺序、开关、状态与这一种的设置；Key 只返回是否已填写与脱敏值。
 * 租户的订阅卡片的模型设置是平台的（只读）。
 */
@Data
public class AiConnectionVO {
    /** api / subscription */
    private String mode;
    private String provider;
    /** 在整个列表中的位置，从 1 开始 */
    private int order;
    private boolean enabled;
    /**
     * 状态：ready（API 已配置）、no_key（API 未配置）、logged_in / logged_out（平台查看订阅）、
     * available / unavailable（租户查看订阅）、unknown（订阅状态获取失败）；暂停中另见 pausedReason
     */
    private String state;
    /** 暂停原因分类码，没有暂停为空 */
    private String pausedReason;
    private LocalDateTime pausedUntil;
    /** 是否已保存过这一种的设置 */
    private boolean saved;
    private boolean hasKey;
    private String apiKeyMasked = "";
    private String parseModel = "";
    private String translateModel = "";
    private String parseEffort = "";
    private String translateEffort = "";
    private Integer maxWebSearches;
    /** 模型设置只读（租户查看平台订阅） */
    private boolean settingsReadonly;
}
