package com.zhul.erp.modules.inquiry.inquiryorder.dto;

import lombok.Data;

import java.util.List;

/**
 * P04 询盘单列表筛选条件（PRD 6.4.1）。同 CustomerInquiryPageQuery，tasks.md 未
 * 单独列出列表查询任务，本轮为支撑前端任务8.1一并补上。
 */
@Data
public class InquiryOrderPageQuery {
    private Integer page = 1;
    private Integer pageSize = 10;
    private String inquiryCode;
    private String brand;
    private String category;
    private List<Integer> statusList;
    private Long assigneeId;
    /** true=只看"我的待处理"（assigneeId=当前用户且未完结） */
    private Boolean mine;
    /** true=只看手动创建（customerInquiryId为空），false=只看AI拆单产生，null=不筛选 */
    private Boolean manualOnly;
}
