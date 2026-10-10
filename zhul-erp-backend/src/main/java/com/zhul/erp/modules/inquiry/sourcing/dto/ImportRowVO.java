package com.zhul.erp.modules.inquiry.sourcing.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 导入询价结果的一行 */
@Data
public class ImportRowVO {
    /** 行在文件中的位置，如「1688 区第 3 行」 */
    private String position;
    private Long itemId;
    private String model;
    private Integer channel;
    private String shopName;
    /** 文件里的原文 */
    private String rawPrice;
    private BigDecimal unitPrice;
    /** 单价里写了「含税」时为 true，税率从文字里识别，没写按 13% */
    private Boolean taxIncluded;
    private Integer taxRate;
    private Boolean noStock;
    private Integer itemCondition;
    private String rawCondition;
    private Integer leadTime;
    private String rawLeadTime;
    private String note;
    /** 需修正的原因，为空表示可直接入库 */
    private List<String> problems;
}
