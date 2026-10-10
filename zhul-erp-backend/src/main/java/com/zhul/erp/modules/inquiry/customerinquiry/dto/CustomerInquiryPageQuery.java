package com.zhul.erp.modules.inquiry.customerinquiry.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/** 客户询盘列表查询 */
@Data
public class CustomerInquiryPageQuery {
    /** 询盘编号、客户名称、型号 */
    private String keyword;
    private List<Integer> statusList;
    private Integer source;
    private Long ownerId;
    private Integer customerType;
    private LocalDate inquiryDateFrom;
    private LocalDate inquiryDateTo;
    private Boolean urgentOnly;
    /** 询盘等级（字典 inquiry_level 码值） */
    private Integer level;
    private Boolean timeoutOnly;
    /** 型号数、总数量范围（含边界），用于筛出多型号、大数量的询盘 */
    private Integer minItemCount;
    private Integer maxItemCount;
    private Integer minTotalQuantity;
    private Integer maxTotalQuantity;
    /** 排序字段：level、totalItemCount、totalQuantity；为空按创建时间倒序 */
    private String sortField;
    /** asc / desc，默认 desc */
    private String sortOrder;
    private Integer page = 1;
    private Integer pageSize = 10;
}
