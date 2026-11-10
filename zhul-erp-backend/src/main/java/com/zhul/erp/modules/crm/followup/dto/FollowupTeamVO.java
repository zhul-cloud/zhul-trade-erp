package com.zhul.erp.modules.crm.followup.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 客户跟进团队视图（第二期 design D10，spec「团队视图」），只读。
 * 时间区间为 [from, to]（含两端），对比的上一周期为 [prevFrom, prevTo]。金额为本位币。
 */
@Data
public class FollowupTeamVO {
    private String period;
    private LocalDate from;
    private LocalDate to;
    private LocalDate prevFrom;
    private LocalDate prevTo;
    private Results results = new Results();
    private List<Owner> owners = new ArrayList<>();
    private List<Stage> stages = new ArrayList<>();
    private Exceptions exceptions = new Exceptions();

    @Data
    public static class Results {
        /** 新建的商机数 */
        private Compare newOpportunities = new Compare();
        /** 第一次发送的报价单数 */
        private Compare quotationsSent = new Compare();
        /** 第一次发送的 PI 数 */
        private Compare pisSent = new Compare();
        /** 新建的销售订单（不含取消和售后补发） */
        private Money orders = new Money();
        /** 签收在区间内的订单中，签收不晚于首次估算预计送达日的单数 / 总单数 */
        private Integer onTime;
        private Integer delivered;
        /** 记为已评价、已邀请、婉拒的次数 */
        private Integer reviewed;
        private Integer invited;
        private Integer declined;
        /** 客户之前已有已完成订单的新订单 */
        private Money repeat = new Money();
        /** 客户经营（第三期）：应联系、其中按时联系的数量，按时率 = 按时 / 应联系（HALF_UP 整数百分比，应联系为 0 时为 null） */
        private Integer accountDue;
        private Integer accountOnTime;
        private Integer accountOnTimePercent;
        /** 老客户新询盘（老客户成交见 repeat） */
        private Compare accountInquiries = new Compare();
        /** 当前沉睡客户数、本期激活数 */
        private Integer dormant;
        private Integer activated;
        /** 主动开发（第四期）：新增、首次触达、回复、转商机、停止；回复率 = 回复 / 首次触达（HALF_UP 整数百分比，首次触达为 0 时为 null） */
        private Integer developNew;
        private Integer developTouched;
        private Integer developReplied;
        private Integer developReplyPercent;
        private Integer developConverted;
        private Integer developStopped;
    }

    @Data
    public static class Compare {
        private Long value;
        private Long prev;
    }

    @Data
    public static class Money {
        private Long count;
        private BigDecimal amountCny;
    }

    @Data
    public static class Owner {
        private Long ownerId;
        private String ownerName;
        /** 五个板块（主动开发 → 客户经营）的跟进对象数，未上线的板块为 0 */
        private List<Integer> boards = new ArrayList<>();
        private Integer todayDue;
        private Integer overdue;
        /** 同一跟进对象连续延后 3 次及以上的数量 */
        private Integer manySnoozes;
        /** 本期、上一周期记跟进次数 */
        private Integer records;
        /** 本期记跟进中的快速记录次数 */
        private Integer quickRecords;
        private Integer prevRecords;
        /** 逾期或多次延后达到 3 */
        private Boolean attention;
    }

    @Data
    public static class Stage {
        private Integer board;
        private String stageCode;
        private String stageName;
        private Integer count;
        /** 平均停留天数（四舍五入到整数），没有对象时为 null */
        private Integer avgDays;
        private Integer refDays;
        /** 平均停留超过参考天数 */
        private Boolean over;
    }

    @Data
    public static class Exceptions {
        private Integer total = 0;
        private List<ExceptionItem> items = new ArrayList<>();
    }

    @Data
    public static class ExceptionItem {
        /** OVERDUE-逾期、SNOOZED-多次延后、FULFIL-履约异常、ISSUE_LONG-售后处理久、DISSATISFIED-不满意 */
        private String type;
        private String typeName;
        private Long itemId;
        private Integer board;
        private Long customerId;
        private String customerName;
        private String stageCode;
        private String stageName;
        private Long ownerId;
        private String ownerName;
        private String currencyCode;
        private BigDecimal amount;
        /** 一句说明 */
        private String text;
    }
}
