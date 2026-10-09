package com.zhul.erp.modules.purchase.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 采购需求按订单视图的一行：订单头与它的需求 */
@Data
public class RequirementOrderVO {
    private Long soId;
    private String soNo;
    private LocalDate salesDate;
    private String customerName;
    private String customerCountry;
    /** 1-新客户、2-老客户 */
    private Integer customerType;
    /** 1-现货、2-期货 */
    private Integer stockType;
    /** 1-PI 转成、2-手动创建 */
    private Integer source;
    private String ownerName;
    /** 型号进度汇总（按筛选后的需求）：已下单、部分下单、草稿中、未安排 */
    private Integer orderedCount;
    private Integer partialCount;
    private Integer draftCount;
    private Integer pendingCount;
    /** 需求中最近的更新时间 */
    private LocalDateTime updateTime;
    private List<RequirementVO> lines;
}
