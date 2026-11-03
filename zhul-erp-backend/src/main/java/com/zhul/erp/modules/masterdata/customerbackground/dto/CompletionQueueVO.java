package com.zhul.erp.modules.masterdata.customerbackground.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 档案补全左侧：待补全的客户（分页）与「今天已补完 N 个客户」 */
@Data
public class CompletionQueueVO {
    private Long total;
    /** 本人今天把完整度提到 5/5 的客户数 */
    private Long todayDone;
    private List<Row> records;

    @Data
    public static class Row {
        private Long customerId;
        private String customerCode;
        private String name;
        private String country;
        /** 经营等级（0-未成交、1-S、2-A、3-B、4-C） */
        private Integer bizGrade;
        /** 潜力评级 0–5 */
        private Integer potentialRating;
        /** 完整度：已完成的模块数（满分 5） */
        private Integer done;
        /** 还差几项核心项 */
        private Integer missingCount;
        /** 已问客户、等回复：最早一次提问距今天数；没有在等为空 */
        private Integer askedDays;
        /** 最近活动：最近询盘、订单或商机的更新时间 */
        private LocalDateTime lastActivity;
    }
}
