package com.zhul.erp.modules.inquiry.customerinquiry.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 历史询价按品牌 + 归一化型号分组 */
@Data
public class PriceHistoryGroupVO {
    private String brand;
    private String model;
    /** 品类：取该组最近一条有品类的型号明细 */
    private String category;
    private String brandKey;
    private String modelKey;
    private BigDecimal minPriceCny;
    private BigDecimal maxPriceCny;
    private Integer recordCount;
    private LocalDateTime lastQuotedAt;
    private Long daysAgo;
    private List<PriceRecordVO> records;
}
