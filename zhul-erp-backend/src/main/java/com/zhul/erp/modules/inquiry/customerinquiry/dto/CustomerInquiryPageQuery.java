package com.zhul.erp.modules.inquiry.customerinquiry.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * P01 客户询盘列表筛选条件（PRD 6.1.1）。tasks.md 未单独列出列表查询任务，但 7.1
 * 前端列表页需要一个可分页/筛选的接口才能实现，因此在本轮一并补上（详见完成报告）。
 */
@Data
public class CustomerInquiryPageQuery {
    private Integer page = 1;
    private Integer pageSize = 10;
    private String inquiryCode;
    /** 客户名称模糊搜索：先按名称匹配 customer，再按 customerId 过滤 */
    private String customerName;
    private List<Integer> statusList;
    private LocalDate inquiryDateFrom;
    private LocalDate inquiryDateTo;
    private Long ownerId;
}
