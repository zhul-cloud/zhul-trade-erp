package com.zhul.erp.modules.masterdata.customerbackground.dto;

import lombok.Data;

/** 发起 AI 背调 */
@Data
public class BackgroundResearchRequest {
    /** 已看过「24 小时内背调过」的提醒并确认再背调 */
    private Boolean confirmed;
}
