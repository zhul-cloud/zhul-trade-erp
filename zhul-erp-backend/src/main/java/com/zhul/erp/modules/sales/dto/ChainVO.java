package com.zhul.erp.modules.sales.dto;

import lombok.Data;

import java.util.List;

/** 来源 / 去向链路：客户询盘 → 报价单 → PI → 销售订单 */
@Data
public class ChainVO {
    private List<Node> inquiries;
    private List<Node> quotations;
    private List<Node> pis;
    private List<Node> orders;

    @Data
    public static class Node {
        private Long id;
        private String no;
        private Integer status;
        private String statusName;
        /** 是当前查看的单据 */
        private Boolean current;
    }
}
