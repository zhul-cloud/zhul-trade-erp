package com.zhul.erp.modules.crm.dto;

import lombok.Data;

import java.util.List;

/** 每日渠道统计：rows 按分组给出，summary 为合计；有效率由前端按一位小数计算 */
@Data
public class OpportunityStatsVO {

    /** channel / owner / date */
    private String groupBy;
    private List<Row> rows;
    private Row summary;

    @Data
    public static class Row {
        /** 分组值：渠道码、业务员 ID 或 yyyy-MM-dd */
        private String key;
        /** 分组名称：渠道名、业务员姓名或日期 */
        private String label;
        private long total;
        private long invalid;
        private long valid;
        private long won;
        private long lost;
    }
}
