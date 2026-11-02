package com.zhul.erp.modules.masterdata.customerprofile.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 经营等级明细（对应《成交客户表》），含参与计算的订单 */
@Data
public class RfmDetailVO {
    private LocalDate firstOrderDate;
    private LocalDate lastOrderDate;
    private Long daysSinceLast;
    private Integer ordersInYear;
    private BigDecimal usdInYear;
    private BigDecimal usdTotal;
    /** 当前美元汇率（折算非美元订单用），没有维护时为空 */
    private BigDecimal usdRate;
    private Integer r;
    private Integer f;
    private Integer m;
    private BigDecimal score;
    private Integer calc;
    private Integer adjust;
    private Integer finalGrade;
    private String adjustReason;
    private String adjustByName;
    private LocalDateTime adjustAt;
    private Integer followDays;
    private LocalDateTime calcAt;
    /** 分界与权重，前端显示各档 */
    private List<Integer> recencyDays;
    private List<Integer> frequency;
    private List<BigDecimal> monetaryUsd;
    private List<BigDecimal> weights;
    private List<BigDecimal> cutoffs;
    private List<OrderRow> orders = new ArrayList<>();

    @Data
    public static class OrderRow {
        private Long id;
        private String soNo;
        private LocalDate salesDate;
        private String currencyCode;
        private BigDecimal amount;
        private BigDecimal amountCny;
        private BigDecimal usd;
    }
}
