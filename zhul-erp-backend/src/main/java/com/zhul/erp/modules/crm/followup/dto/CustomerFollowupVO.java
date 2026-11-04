package com.zhul.erp.modules.crm.followup.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 客户详情「跟进」分区：当前所在阶段与下一步、概况，以及时间线第一页 */
@Data
public class CustomerFollowupVO {
    /** 当前最紧急的跟进对象，没有在跟进的为空 */
    private FollowupItemVO current;
    /** 五个板块里，这位客户走到第几步（1–5），0 为还没进入 */
    private Integer step;
    private LocalDateTime lastContactAt;
    private String lastChannelName;
    private Integer recent30;
    private Integer noReplyStreak;
    private Integer quoteCount;
    private Integer piCount;
    private List<Amount> openAmounts = new ArrayList<>();
    private Long total;
    private List<FollowupRecordVO> records = new ArrayList<>();

    @Data
    public static class Amount {
        private String currencyCode;
        private BigDecimal amount;
    }
}
