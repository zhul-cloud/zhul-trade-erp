package com.zhul.erp.modules.inquiry.customerinquiry.dto;

import lombok.Data;

import java.util.List;

/** 解析确认页的一行（AI 解析结果或手动录入的空行） */
@Data
public class DraftRowVO {
    private String brand;
    private String category;
    /** 关联的品类（细分或一级），关联不上时为空 */
    private Long categoryId;
    private String originalModel;
    private String confirmedModel;
    private Integer confidence;
    private String correctionNote;
    private Integer quantity;
    private String unit;
    private String description;
    /** 英文描述（给客户看的单据用；系统内显示中文 description） */
    private String descriptionEn;
    private Integer lifecycle;
    private String replacementModel;
    private Integer difficulty;
    private String inquiryScript;
    private List<String> searchKeywords;
    private PriceMatchVO match;
}
