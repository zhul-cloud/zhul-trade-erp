package com.zhul.erp.modules.product.dto;

import lombok.Data;

/** 各缺项对应的商品数量（未删除的商品），与 GET /products?missing= 的筛选结果条数一致。 */
@Data
public class CompletenessSummaryVO {
    private Long total;
    private Long missingMedia;
    private Long missingLogistics;
    private Long missingCustoms;
    private Long missingPrice;
}
