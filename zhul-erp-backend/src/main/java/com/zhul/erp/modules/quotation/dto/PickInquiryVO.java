package com.zhul.erp.modules.quotation.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/** 挑选型号报价：一个询盘及其型号（搜索型号时只含命中的型号） */
@Data
@EqualsAndHashCode(callSuper = true)
public class PickInquiryVO extends QuoteInquiryVO {
    private List<PickItemVO> items;
}
