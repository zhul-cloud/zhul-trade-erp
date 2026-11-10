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
    /** 当前跟进对象所在板块的全部单据（商机成交、订单履约、售后评价），记一次跟进时默认全选；其他板块只有当前这一张 */
    private List<FollowupItemVO> currentItems = new ArrayList<>();
    /** 五个板块里，这位客户走到第几步（1–5），0 为还没进入 */
    private Integer step;
    /** 这位客户有在跟进对象的板块（第二期：链路卡片上逐个标出），按板块顺序 */
    private List<Integer> boards = new ArrayList<>();
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
