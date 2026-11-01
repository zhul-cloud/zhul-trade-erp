package com.zhul.erp.modules.inquiry.sourcing.dto;

import lombok.Data;

import java.util.List;

/** 我的询价「待认领」：当前页任务 + 全部页的品牌汇总 */
@Data
public class SalesPendingVO {
    private Long total;
    private List<BrandGroupVO> brands;
    private List<SalesPendingTaskVO> tasks;
}
