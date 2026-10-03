package com.zhul.erp.modules.inquiry.sourcing.dto;

import lombok.Data;

import java.util.List;

/** 我的询价任务中的一个型号 */
@Data
public class MyTaskItemVO {
    private Long id;
    private String model;
    private String originalModel;
    private Integer quantity;
    private String unit;
    private String description;
    private Integer lifecycle;
    private String replacementModel;
    private Integer difficulty;
    private String inquiryScript;
    private List<String> searchKeywords;
    private List<MyQuoteVO> quotes;
}
