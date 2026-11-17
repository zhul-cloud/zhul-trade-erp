package com.zhul.erp.modules.product.content.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 商品最近一次 AI 生成（列表「AI 生成」列） */
@Data
public class ContentAiJobVO {
    private Long id;
    /** 1-排队中、2-生成中、3-待确认、4-已导入、5-失败、6-已取消 */
    private Integer status;
    /** 排队中时前面还有几个 */
    private Integer queueAhead;
    private String failReason;
    private LocalDateTime createTime;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime confirmedAt;
    private String confirmedBy;
    /** 资料包、营销包是否已写入 */
    private boolean factsImported;
    private boolean marketingImported;
}
