package com.zhul.erp.modules.logistics.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 货代月结对账单 */
@Data
public class StatementVO {
    private Long id;
    private Long forwarderId;
    private String forwarderName;
    /** YYYY-MM */
    private String period;
    private Integer status;
    private String statusName;
    private Integer shipmentCount;
    private BigDecimal ourTotal;
    private BigDecimal statementTotal;
    private BigDecimal diffTotal;
    private Integer diffCount;
    private String confirmedByName;
    private LocalDateTime confirmedAt;
    /** 详情才有 */
    private List<Line> lines;
    private LocalDateTime createTime;
    private String createBy;
    private LocalDateTime updateTime;
    private String updateBy;

    @Data
    public static class Line {
        private Long id;
        private Long logisticsId;
        private String shNo;
        private LocalDate shippedDate;
        private String carrier;
        private String waybillNo;
        private String customerName;
        private BigDecimal ourFreight;
        private BigDecimal statementAmount;
        private BigDecimal diff;
        private String note;
    }
}
