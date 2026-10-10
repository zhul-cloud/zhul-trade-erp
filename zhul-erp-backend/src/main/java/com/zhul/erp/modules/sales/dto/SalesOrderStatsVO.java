package com.zhul.erp.modules.sales.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 销售订单列表顶部统计（数据范围内的有效订单；「本月」按销售日期 / 到账日期） */
@Data
public class SalesOrderStatsVO {
    private Long monthCount;
    private Long inProgressCount;
    /** 进行中订单按状态分布（按推进顺序，只列有订单的状态） */
    private List<ProgressCount> progressCounts;
    /** 本月实收人民币：本月到账的有效收款（含平台收款）的实收人民币 */
    private BigDecimal monthNetCny;
    private BigDecimal monthOnlineCny;
    private BigDecimal monthOfflineCny;
    /** 有型号未指定采购员的进行中订单数 */
    private Long unassignedCount;

    @Data
    public static class ProgressCount {
        private String code;
        private String name;
        private Long count;
    }
}
