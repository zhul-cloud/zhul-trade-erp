package com.zhul.erp.modules.product.content.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 某平台的不发布标记（本公司） */
@Data
public class MarketingSkipVO {
    private String markedBy;
    private LocalDateTime markedAt;
}
