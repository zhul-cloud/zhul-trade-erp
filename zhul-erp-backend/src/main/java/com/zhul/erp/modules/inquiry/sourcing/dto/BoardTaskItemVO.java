package com.zhul.erp.modules.inquiry.sourcing.dto;

import lombok.Data;

/** 任务详情里的一个型号（不含价格与客户信息） */
@Data
public class BoardTaskItemVO {
    private Long id;
    private String model;
    /** 客户原文里的写法 */
    private String originalModel;
    private Integer quantity;
    private String unit;
    private String description;
    private Integer lifecycle;
    private String replacementModel;
    private Integer difficulty;
    /** 回价状态（1-待询价、2-已回价、3-无货） */
    private Integer quoteStatus;
    /** 当前采购成本价对应的询价记录 */
    private Long selectedQuoteId;
    /** 成本价是否由采购负责人手动指定 */
    private Boolean costManual;
    /** 各采购已提交的询价记录（含推荐标记；渠道、店铺按「查看货源信息」权限返回） */
    private java.util.List<com.zhul.erp.modules.inquiry.customerinquiry.dto.PriceRecordVO> quotes;
    /** 修改过的次数（采购修改回价 + 采购负责人调整成本价），大于 0 时显示「修改记录」入口 */
    private Integer changeCount;
}
