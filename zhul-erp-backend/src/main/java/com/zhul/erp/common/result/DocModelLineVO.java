package com.zhul.erp.common.result;

import lombok.Data;

import java.math.BigDecimal;

/** 单据列表「查看」弹窗里的一行型号（报价单、PI 共用） */
@Data
public class DocModelLineVO {
    private Long id;
    private String brand;
    private String model;
    private String category;
    private Integer quantity;
    private BigDecimal unitPrice;
    private String currencyCode;
}
