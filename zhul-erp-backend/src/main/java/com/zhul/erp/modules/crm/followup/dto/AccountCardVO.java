package com.zhul.erp.modules.crm.followup.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 客户经营作战卡顶部（第三期 spec「客户经营列表与作战卡」）：G1 分层、G2 档案、G3 采购规律，
 * 触达理由、交叉销售建议、战略客户与年度计划。金额均为本位币 CNY。
 */
@Data
public class AccountCardVO {
    private Long customerId;
    /** G1：经营等级（S/A/B/C）与 RFM 综合分 */
    private String bizGrade;
    private BigDecimal bizGradeScore;
    /** 该等级的联系间隔（天），记跟进「当前没有需求」的默认下一步日期用 */
    private Integer followDays;
    /** G2：档案完成几个模块 / 共几个 */
    private Integer profileDone;
    private Integer profileTotal;
    /** G3：采购规律（系统推测） */
    private String patternText;
    private Integer patternCycleDays;
    private LocalDate patternNextDate;
    private String patternBrands;
    /** 近 12 个月成交 */
    private String currencyCode = "CNY";
    private BigDecimal recentAmount;
    private Integer recentOrderCount;
    private LocalDate lastOrderDate;
    /** 最近一单的主要明细（品牌 型号 × 数量），AI 写触达消息用 */
    private String lastOrderItems;
    private String reason;
    private LocalDate dormantSince;
    private Integer dormantDays;
    private String crossCategory;
    private List<CrossSell> crossSell = new ArrayList<>();
    /** 战略客户 */
    private Boolean strategic;
    private LocalDateTime strategicAt;
    private Boolean suggestStrategic;
    private LocalDate lastReviewDate;
    private LocalDate nextReviewDate;
    private Plan plan;

    @Data
    public static class CrossSell {
        private String category;
        /** 也买过这个品类的其他客户数 */
        private Integer customers;
    }

    @Data
    public static class Plan {
        private Integer planYear;
        private String currencyCode = "CNY";
        /** 年度目标，未填为 null */
        private BigDecimal targetAmount;
        private String focus;
        private String note;
        /** 今年已成交：未取消订单本位币合计，不含售后补发 */
        private BigDecimal dealtAmount;
        /** 完成比例（整数百分比，HALF_UP），没有目标时为 null */
        private Integer percent;
    }
}
